package cz.sa.ybus.core.trafficcontrol.service;

import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

import com.sun.istack.Nullable;

import cz.sa.ybus.core.busconnection.service.BusConnectionVehicleService;
import cz.sa.ybus.core.busconnection.service.dos.Bus1DO;
import cz.sa.ybus.core.busconnection.service.dos.BusType1DO;
import cz.sa.ybus.core.common.annotations.NotNull;
import cz.sa.ybus.core.common.service.AbstractService;
import cz.sa.ybus.core.trafficcontrol.service.dos.TrainCompositionDO;
import cz.sa.ybus.core.trafficcontrol.service.dos.WagonDO;
import cz.sa.ybus.domain.bus.BCVehicleID;
import cz.sa.ybus.domain.bus.BusConnectionID;
import cz.sa.ybus.domain.bus.BusID;
import cz.sa.ybus.domain.bus.BusTypeID;
import cz.sa.ybus.domain.bus.VehicleCategory;
import cz.sa.ybus.server.domain.busconnection.BusConnection;
import cz.sa.ybus.server.domain.busconnection.BusConnectionVehicle;
import cz.sa.ybus.server.domain.busconnection.BusConnectionsCache;
import cz.sa.ybus.server.domain.busdao.BusDao;
import cz.sa.ybus.server.domain.busdao.BusService;
import cz.sa.ybus.server.infrastructure.provider.trafficcontrol.TrafficControlProvider;
import cz.sa.ybus.server.logging.LogBefore;

/**
 * 
 * @author dalibor.dobes
 *
 */
@ManagedResource
@Service
public class TrafficControlServiceImpl extends AbstractService implements TrafficControlService {

  @Autowired
  private TrafficControlProvider trafficControlProvider;
  @Autowired
  private BusDao busDao;
  @Autowired
  private BusService busService;
  @Autowired
  private BusConnectionsCache busConnectionsCache;
  @Autowired
  private BusConnectionVehicleService busConnectionVehicleService;
  
  private final Logger log = LoggerFactory.getLogger(getClass());
  
  private static final String RJ = "RJ";
  
  @ManagedOperation
  @Override
  @LogBefore
  public void updateTrainStructure() {

    final DateTime now = DateTime.now();
    final DateTime todayMidnight = now.withTimeAtStartOfDay();
    final DateTime midnightLast = todayMidnight.plusDays(2).withTimeAtStartOfDay();
    final VehicleCategory vehicleCategory = VehicleCategory.RAIL_CAR;

    final List<Long> busConnectionVOs = busDao.getBusConnectionsByDepartureTimeAndVehicleType(todayMidnight, midnightLast,
        vehicleCategory);

    final List<BusConnection> busConnections = busConnectionsCache.getBusConnections(busConnectionVOs);
    trafficControlProvider.updateTrainCompositions(tcDO -> updateTrainVehicles(tcDO, busConnections));
  }

  private void updateTrainVehicles(@NotNull final TrainCompositionDO trainCompositionDO, @NotNull final List<BusConnection> busConnections) {
    for (BusConnection busConnection : busConnections) {
      final BusConnectionID busConnectionID = busConnection.getID();
      final LocalDate dateDO = busConnection.getDeparture().toLocalDate();
      final String trainCodeDO = busConnection.getConnectionCode();
      final List<BusConnectionVehicle> busConnectionVehicles = busConnection.getVehicles();
      final List<WagonDO> wagonDOs = trainCompositionDO.getWagons();
      if (wagonDOs.size() != busConnectionVehicles.size()) {
        log.warn("The number of wagons does not match: " + wagonDOs.size() + " is not the same " + busConnectionVehicles.size()
            + " from database");
      }
      final LocalDate dateCSV = trainCompositionDO.getLocalDate();
      final String trainCodeCSV = RJ + " " + trainCompositionDO.getTrainName();
      if ((dateDO.equals(dateCSV)) && (trainCodeCSV).equals(trainCodeDO)) {
        for (final BusConnectionVehicle busConnectionVehicle : busConnectionVehicles) {
          final BCVehicleID bcVehicleID = busConnectionVehicle.getID();
          final Integer vehiclePosition = busConnectionVehicle.getPosition();
          final BusType1DO busType = busConnectionVehicleService
              .getBusType1DO(BusTypeID.forId(busConnectionVehicle.getBusTypeId()));
          for (final WagonDO wagonDO : wagonDOs) {
            final Integer wagonPositionCSV = wagonDO.getPositionInTrain();
            final String wagonId = wagonDO.getWagonId();
            if (vehiclePosition.intValue() == wagonPositionCSV.intValue()) {
              updateBusConnectionBusVehicle(busConnectionID, bcVehicleID, busType, wagonId);
            }
          }
        }
      }
    }
  }

  private void updateBusConnectionBusVehicle(
      @NotNull final BusConnectionID busConnectionID,
      @NotNull final BCVehicleID bcVehicleID,
      @NotNull final BusType1DO busType,
      @NotNull final String wagonId
  ) {
    /* bude potreba prvne ulozit bus/wagon pokud jeste neexistuje */
    final List<Bus1DO> allWagons = getAllWagons();

    if (!allWagons.stream().anyMatch(bus -> bus.getSpz().equals(wagonId))) {
      final Integer maxNumber = allWagons.stream().map(bus -> bus.getNumber()).max(Integer::compareTo)
          .orElse(BusService.INITIAL_WAGON_NUMBER - 1) + 1;
      final Bus1DO wagon = new Bus1DO();
      wagon.setNumber(maxNumber);
      wagon.setSpz(wagonId);
      wagon.setBusType(busType);
      wagon.setActive(true);

      busService.saveBus(wagon);
    }
    changeBus(busConnectionID, bcVehicleID, wagonId);
  }

  @Nullable
  private List<Bus1DO> getAllWagons() {
    final List<Bus1DO> allBuses = busService.getAllBuses();
    return allBuses.stream().filter(bus ->bus.getBusType().getVehicleCategory() == VehicleCategory.RAIL_CAR).collect(Collectors.toList());
  }

  private void changeBus(
      @NotNull final BusConnectionID busConnectionID,
      @NotNull final BCVehicleID bcVehicleID,
      @NotNull final String wagonId
  ) {
    final List<Bus1DO> allWagons = getAllWagons();
    final Bus1DO wagon = allWagons.stream().filter(bus -> bus.getSpz().equals(wagonId)).findAny().get();
    final BusID busID = BusID.forId(wagon.getId());
    busService.changeBus(busConnectionID, bcVehicleID, busID);
  }
}
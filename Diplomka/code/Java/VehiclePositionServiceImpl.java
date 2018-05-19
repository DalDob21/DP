package cz.sa.ybus.core.vehiclePosition.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

import cz.sa.ybus.core.common.annotations.NotNull;
import cz.sa.ybus.core.common.service.AbstractService;
import cz.sa.ybus.core.vehiclePosition.service.dos.PositionDO;
import cz.sa.ybus.core.vehiclePosition.service.dos.TrackedVehiclePathDO;
import cz.sa.ybus.core.vehiclePosition.service.dos.VehicleInfoDO;
import cz.sa.ybus.server.infrastructure.dao.vehiclePosition.VehiclePositionDao;
import cz.sa.ybus.server.infrastructure.provider.vehiclePosition.VehiclePositionProvider;

@Service
@ManagedResource
public class VehiclePositionServiceImpl extends AbstractService implements VehiclePositionService {

  @Autowired
  private VehiclePositionProvider vehiclePositionProvider;
  
  @Autowired
  private VehiclePositionDao vehiclePositionDao;
  
  private static final String SPZ = "B017"; //testovaci vehicle spz
  private static final DateTime startTrackingPath = DateTime.now().plusMinutes(1);
  private static final DateTime endTrackingPath = DateTime.now().plusMinutes(5);
  
  private Set<VehicleInfoDO> loadedActiveVehicles = new HashSet<>();
  private TrackedVehiclePathDO track = new TrackedVehiclePathDO();
  
  private boolean startTracking;
  private boolean endTracking;

  /* cron bude potreba poustet jednou denne - seznam aktivnich vozu se zas tak casto nemeni */
  @Override
  public Set<VehicleInfoDO> loadVehicles() {
    final List<VehicleInfoDO> vehicleInfos = vehiclePositionProvider.getVehicleInfos();
    for (final VehicleInfoDO vehicleInfoDO : vehicleInfos) {
      if (!vehicleInfoDO.getVehicleName().isEmpty()) {
        loadedActiveVehicles.add(vehicleInfoDO);
      }
    }
    return loadedActiveVehicles;
  }

  /* cron nastavit na 5 sekund */
  @Override
  @ManagedOperation
  public void trackVehiclePath(/*@NotNull final String spz,@NotNull final DateTime startTrackingPath, @NotNull final DateTime endTrackingPath*/) {
    
    final LocalDateTime currentDateTime = LocalDateTime.now();
    final LocalDateTime start = startTrackingPath.toLocalDateTime();
    final LocalDateTime end = endTrackingPath.toLocalDateTime();
    
    startTracking = currentDateTime.isAfter(start); //boolean k zapnuti trasovani
    
    endTracking = currentDateTime.isBefore(end); // boolean k vypnuti a ulozeni
    
    if (startTracking && endTracking) {
      trackConnectionPath();
    }
    else {
      saveTrackPath();
    }
  }
  
  /* jedna metoda pro aktualizaci pozic */
  private Map<Long, PositionDO> getAllVehiclePositions() {
    final Map<Long, PositionDO> positionMap = vehiclePositionProvider.getPositions();
    return positionMap;
  }
  
  /* druha metoda pro ukladani cesty podle spz */
  private TrackedVehiclePathDO trackConnectionPath(/*@NotNull final String spz,*/) {
    
    if (loadedActiveVehicles.isEmpty()) {
      loadedActiveVehicles.addAll(loadVehicles());
    }

    final Map<Long, PositionDO> positionMap = getAllVehiclePositions();
    
    final VehicleInfoDO vehicleInfoDO = loadedActiveVehicles.stream().filter(vehicleInfo -> vehicleInfo.getVehicleName().equals(SPZ)).findFirst().get();
    
//    for (final VehicleInfoDO vehicleInfoDO : loadedActiveVehicles) {
//      if (!vehicleInfoDO.getVehicleName().equals(SPZ)) {
//        continue;
//      }
    for (final Map.Entry<Long, PositionDO> entry : positionMap.entrySet()) {
      final Long mapId = entry.getKey();
      final PositionDO positionDO = entry.getValue();
      final Long infoId = vehicleInfoDO.getVehicleId();
      if (mapId.longValue() == infoId.longValue()) {
        final TrackedVehiclePathDO updatedVehiclePathDO = updateTrackedVehiclePosition(vehicleInfoDO, positionDO, track);
        if (updatedVehiclePathDO == null) {
          continue;
        }
        track = updatedVehiclePathDO;
      }
    }
//    }

    return track;
  }

  @Nullable
  private TrackedVehiclePathDO updateTrackedVehiclePosition(
      @NotNull final VehicleInfoDO vehicleInfoDO,
      @NotNull final PositionDO positionDO,
      @NotNull final TrackedVehiclePathDO track
  ) {
    TrackedVehiclePathDO trackedVehiclePath = new TrackedVehiclePathDO();
    if (track.getPositionDOs() == null) {
      trackedVehiclePath = createTrackedVehiclePathDO(vehicleInfoDO, positionDO);
    }
    else {
      final Long tvpId = track.getVehicleId();
      if (vehicleInfoDO.getVehicleId().longValue() == tvpId.longValue()) {
        /* pokud se nam vraci stejny cas, ktery mame ulozeny v TrackedVehiclePathDO tak s nejvetsi pravdepodobnosti vuz stoji a je zbytecne ukladat tuto souradnici vicekrat */
        if (positionDO.getDateTime().longValue() == track.getLastPosition().getDateTime().longValue()
            && positionDO.getLatitude().doubleValue() == track.getLastPosition().getLatitude().doubleValue()
            && positionDO.getLongitude().doubleValue() == track.getLastPosition().getLongitude().doubleValue()) {
          return null;
        }
        track.setLastPosition(positionDO);
        final List<PositionDO> trackedPositions = track.getPositionDOs();
        trackedPositions.add(positionDO);
        track.setPositionDOs(trackedPositions);
        return track;
      }
    }

    return trackedVehiclePath;
  }

  @NotNull
  private TrackedVehiclePathDO createTrackedVehiclePathDO(
      @NotNull final VehicleInfoDO vehicleInfoDO,
      @NotNull final PositionDO positionDO
  ) {
    final Long infoId = vehicleInfoDO.getVehicleId();
    final String vehicleName = vehicleInfoDO.getVehicleName();
    final TrackedVehiclePathDO trackedVehiclePath = new TrackedVehiclePathDO();
    trackedVehiclePath.setLastPosition(positionDO);
    trackedVehiclePath.setVehicleId(infoId);
    trackedVehiclePath.setVehicleName(vehicleName);
    final List<PositionDO> positions = new ArrayList<>();
    positions.add(positionDO);
    trackedVehiclePath.setPositionDOs(positions);
    return trackedVehiclePath;
  }
  
  private void saveTrackPath() {
    
    if (track.getPositionDOs() == null) {
      System.out.println("Neni nic natrasovano");
      return;
    }
    else {
      vehiclePositionDao.saveVehiclePositionPath(); //dodelat dao
//      track.getVehicleId(); //blabla dodelat
      
      // testovaci vypis na konzoli
      /* simulace ulozeni dat */
      String name = track.getVehicleName();
      System.out.println("Ukladam: "+name);
      List<PositionDO> pos = track.getPositionDOs();
      for (PositionDO p : pos) {
        Double la = p.getLatitude();
        Double lo = p.getLongitude();
        Long da = p.getDateTime();
        System.out.println("--------> " +" lat: "+la+" long: "+lo+" time: "+da);
        /* vycistit track */
        track = new TrackedVehiclePathDO();
      }
    }
  }
  
  /* treti metoda pro vypocet zpozdeni z namerenych hodnot */ 
  private Integer calculateDelay(/*@NotNull final String spz,*/) {
    
    final VehicleInfoDO vehicleInfoDO = loadedActiveVehicles.stream().filter(vehicleInfo -> vehicleInfo.getVehicleName().equals(SPZ)).findFirst().get();

    
    return null;
  }
  
}
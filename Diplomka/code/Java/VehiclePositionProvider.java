package cz.sa.ybus.server.infrastructure.provider.vehiclePosition;

import java.util.List;
import java.util.Map;

import cz.sa.ybus.core.vehiclePosition.service.dos.PositionDO;
import cz.sa.ybus.core.vehiclePosition.service.dos.VehicleInfoDO;

public interface VehiclePositionProvider {
  
  List<VehicleInfoDO> getVehicleInfos(); //vrati info o vozech
  
  Map<Long, PositionDO> getPositions(); // vrati poluhu bodu
}

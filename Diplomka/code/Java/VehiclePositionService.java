package cz.sa.ybus.core.vehiclePosition.service;

import java.util.Set;

import cz.sa.ybus.core.common.service.Service;
import cz.sa.ybus.core.vehiclePosition.service.dos.VehicleInfoDO;

public interface VehiclePositionService extends Service {
  
//  List<TrackedVehiclePathDO> getVehiclePosition();//pujde pryc
  
  Set<VehicleInfoDO> loadVehicles();
  
  void trackVehiclePath(/*String spz,DateTime start, DateTime end*/);

}
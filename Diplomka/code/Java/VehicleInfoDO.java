package cz.sa.ybus.core.vehiclePosition.service.dos;

import java.io.Serializable;

public class VehicleInfoDO implements Serializable {
  
  private Long vehicleId;
  private String vehicleName;
  
  public Long getVehicleId() {
    return vehicleId;
  }
  
  public void setVehicleId(Long vehicleId) {
    this.vehicleId = vehicleId;
  }
  
  public String getVehicleName() {
    return vehicleName;
  }
  
  public void setVehicleName(String vehicleName) {
    this.vehicleName = vehicleName;
  } 
}
package cz.sa.ybus.core.vehiclePosition.service.dos;

import java.io.Serializable;
import java.util.List;

public class TrackedVehiclePathDO extends VehicleInfoDO implements Serializable {
  
  private PositionDO lastPosition;
  private List<PositionDO> positionDOs;

  public PositionDO getLastPosition() {
    return lastPosition;
  }

  public void setLastPosition(PositionDO lastPosition) {
    this.lastPosition = lastPosition;
  }

  public List<PositionDO> getPositionDOs() {
    return positionDOs;
  }

  public void setPositionDOs(List<PositionDO> positionDOs) {
    this.positionDOs = positionDOs;
  }

}
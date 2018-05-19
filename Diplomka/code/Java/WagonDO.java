package cz.sa.ybus.core.trafficcontrol.service.dos;

import java.io.Serializable;

public class WagonDO implements Serializable {
  private Integer positionInTrain;
  private String wagonId;
  
  public String getWagonId() {
    return wagonId;
  }
  
  public void setWagonId(String wagonId) {
    this.wagonId = wagonId;
  }
  
  public Integer getPositionInTrain() {
    return positionInTrain;
  }
  
  public void setPositionInTrain(Integer positionInTrain) {
    this.positionInTrain = positionInTrain;
  }
}
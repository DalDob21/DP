package cz.sa.ybus.core.trafficcontrol.service.dos;

import java.io.Serializable;
import java.util.List;

import org.joda.time.LocalDate;


public class TrainCompositionDO implements Serializable {
  private LocalDate date;
  private Integer trainName;
  private List<WagonDO> wagons;

  public LocalDate getLocalDate() {
    return date;
  }
  
  public void setLocalDate(LocalDate date) {
    this.date = date;
  }
  
  public Integer getTrainName() {
    return trainName;
  }
  
  public void setTrainName(Integer trainName) {
    this.trainName = trainName;
  }
  
  public List<WagonDO> getWagons() {
    return wagons;
  }
  
  public void setWagons(List<WagonDO> wagons) {
    this.wagons = wagons;
  }
}
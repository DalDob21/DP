package cz.sa.ybus.core.vehiclePosition.service.dos;

import java.io.Serializable;

public class PositionDO implements Serializable {

  private Double latitude;
  private Double longitude;
  private Long dateTime;

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public Long getDateTime() {
    return dateTime;
  }

  public void setDateTime(Long dateTime) {
    this.dateTime = dateTime;
  }
}
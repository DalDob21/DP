package cz.sa.ybus.server.infrastructure.provider.szdc;

import java.util.List;

import cz.sa.ybus.core.szdc.service.dos.StationDO;
import cz.sa.ybus.core.szdc.service.dos.TrainPositionDO;
import cz.sa.ybus.core.szdc.service.dos.ViewDO;
import cz.sa.ybus.core.szdc.service.enums.DirectionEnum;

public interface SzdcProvider {
  
  List<StationDO> getStations();
  
  List<TrainPositionDO> getTrainPositions();
  
  List<ViewDO> getViews(List<Integer> sr70numbers, DirectionEnum departure);

}
package cz.sa.ybus.server.infrastructure.provider.trafficcontrol;

import java.util.function.Consumer;

import cz.sa.ybus.core.common.annotations.NotNull;
import cz.sa.ybus.core.trafficcontrol.service.dos.TrainCompositionDO;

public interface TrafficControlProvider {
  
  void updateTrainCompositions(@NotNull final Consumer<TrainCompositionDO> trainCompositionUpdater);

}

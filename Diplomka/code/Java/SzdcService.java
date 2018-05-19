package cz.sa.ybus.core.szdc.service;

import cz.sa.ybus.core.common.service.Service;

public interface SzdcService extends Service {

  void updatePlatforms();
  
  void updateDelays();

}

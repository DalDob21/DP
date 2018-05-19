package cz.sa.ybus.server.infrastructure.provider.szdc;

import java.net.URL;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cz.sa.szdcPosition.api.TrainPosition;
import cz.sa.szdcPosition.api.TrainPositionSoap;
import cz.sa.ybus.impl.common.general.Config;
import cz.sa.ybus.impl.common.general.ConfigKeys;
import cz.sa.ybus.server.infrastructure.util.webservice.transport.HandlerChainProvider;

@Service
public class SzdcPositionWSApiService{

  @Autowired
  private Config config;
  private TrainPosition service;
  private final String RESOURCE = "SZDCPositionApiWebService.wsdl";
  private final String NAMESPACE = "http://provoz.szdc.cz/grappws/";
  private final String LOCALPART = "TrainPosition";

  public TrainPositionSoap getSoap() {
    TrainPositionSoap port = getService().getTrainPositionSoap();
    addPortProperties((BindingProvider) port);
    return port;
  }

  private TrainPosition getService() {
    if (service == null) {
      URL resource = TrainPosition.class.getResource(RESOURCE);
      service = new TrainPosition(resource, new QName(NAMESPACE, LOCALPART));
      service.setHandlerResolver(new HandlerChainProvider());
    }
    return service;
  }

  private void addPortProperties(BindingProvider provider) {
    final Map<String, Object> requestContext = provider.getRequestContext();
    requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, config.getProperty(ConfigKeys.SZDC_POSITION_API_ENDPOINT));
  }
}

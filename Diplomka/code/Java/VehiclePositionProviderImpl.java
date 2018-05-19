package cz.sa.ybus.server.infrastructure.provider.vehiclePosition.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.MapType;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

import cz.sa.ybus.core.common.annotations.NotNull;
import cz.sa.ybus.core.vehiclePosition.service.dos.PositionDO;
import cz.sa.ybus.core.vehiclePosition.service.dos.VehicleInfoDO;
import cz.sa.ybus.impl.common.general.Config;
import cz.sa.ybus.impl.common.general.ConfigKeys;
import cz.sa.ybus.server.infrastructure.provider.vehiclePosition.VehiclePositionProvider;
import cz.sa.ybus.server.infrastructure.provider.vehiclePosition.domain.Position;
import cz.sa.ybus.server.infrastructure.provider.vehiclePosition.domain.Vehicle;
import cz.sa.ybus.server.infrastructure.provider.vehiclePosition.domain.VehicleInfos;
import cz.sa.ybus.server.infrastructure.provider.vehiclePosition.domain.VehiclePosition;

@Service
public class VehiclePositionProviderImpl implements VehiclePositionProvider {

  @Autowired
  private Config config;

  private static final String INIT_VEHICLES_PATH = "/init-vehicles/";
  private static final String DATA = "/data/";
  private static final String JSON = "json";
  
  private final Logger log = LoggerFactory.getLogger(getClass());

  private WebClient getWebClient() {

    final String vehiclesEndpoint = config.getProperty(ConfigKeys.VEHICLE_POSITION_ENDPOINT);

    final WebClient webClient = WebClient.create(vehiclesEndpoint, Collections.singletonList(new JacksonJaxbJsonProvider()));
    ClientConfiguration clientConfiguration = WebClient.getConfig(webClient);
    clientConfiguration.getInInterceptors().add(new LoggingInInterceptor());

    return webClient;
  }

  @Override
  public List<VehicleInfoDO> getVehicleInfos() {

    final String code = config.getProperty(ConfigKeys.VEHICLE_POSITION_CODE);

    final WebClient webClient = getWebClient();
    webClient.path(INIT_VEHICLES_PATH + code);
    webClient.type(MediaType.APPLICATION_JSON_TYPE);
    webClient.accept(MediaType.APPLICATION_JSON_TYPE);

    final String resp = webClient.get(String.class);
    final ObjectMapper mapper = new ObjectMapper();
    final TypeFactory typeFactory = mapper.getTypeFactory();

    List<VehicleInfos> listVehicleInfos = new ArrayList<>();

    try {
      listVehicleInfos = mapper.readValue(resp, typeFactory.constructCollectionType(List.class, VehicleInfos.class));
    }
    catch (JsonParseException e ) {
      log.error("Deserialization error: " + e);
    }
    catch (JsonMappingException e) {
      log.error("Deserialization error: " + e);
    }
    catch (IOException e) {
      log.error("Deserialization error: " + e);
    }

    final List<Vehicle> vehicles = new ArrayList<>();

    for (final VehicleInfos vehicleInfos : listVehicleInfos) {
      for (final Vehicle vehicle : vehicleInfos.getVehicles()) {
        vehicles.add(vehicle);
      }
    }

    return convertVehicles(vehicles);
  }
  
  @NotNull
  private List<VehicleInfoDO> convertVehicles(@NotNull final List<Vehicle> vehicles) {
    final List<VehicleInfoDO> vehicleInfoDOs = new ArrayList<>();
    for (final Vehicle vehicle : vehicles) {
      final VehicleInfoDO vehicleInfoDO = new VehicleInfoDO();
      vehicleInfoDO.setVehicleId(Long.parseLong(vehicle.getVehicleId()));
      vehicleInfoDO.setVehicleName(vehicle.getVehicleTitle());
      vehicleInfoDOs.add(vehicleInfoDO);
    }

    return vehicleInfoDOs;
  }

  @Override
  public Map<Long, PositionDO> getPositions() {

    final String code = config.getProperty(ConfigKeys.VEHICLE_POSITION_CODE);

    final WebClient webClient = getWebClient();
    webClient.path(DATA + code + "." + JSON);
    webClient.type(MediaType.APPLICATION_JSON_TYPE);
    webClient.accept(MediaType.APPLICATION_JSON_TYPE);

    final String resp = webClient.get(String.class);
    final ObjectMapper mapper = new ObjectMapper();
    final TypeFactory typeFactory = mapper.getTypeFactory();
    final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, VehiclePosition.class);

    Map<String, VehiclePosition> positionMap = Maps.newHashMap();

    try {
      positionMap = mapper.readValue(resp, mapType);
    }
    catch (JsonParseException e) {
      log.error("Deserialization error: " + e);
    }
    catch (JsonMappingException e) {
      log.error("Deserialization error: " + e);
    }
    catch (IOException e) {
      log.error("Deserialization error: " + e);
    }

    return convertPositions(positionMap);
  }

  @NotNull
  private Map<Long, PositionDO> convertPositions(@NotNull final Map<String, VehiclePosition> positionMap) {
    final Map<Long, PositionDO> positionMapDO = Maps.newHashMap();
    for (final Map.Entry<String, VehiclePosition> entry: positionMap.entrySet()) {
      final PositionDO positionDO = new PositionDO();
      final Long id = Long.parseLong(entry.getKey());
      final Position position = entry.getValue().getPosition();
      positionDO.setLatitude(Double.parseDouble(position.getLatitude()));
      positionDO.setLongitude(Double.parseDouble(position.getLongitude()));
      positionDO.setDateTime(Long.parseLong(position.getTime()));
      positionMapDO.put(id, positionDO);
    }

    return positionMapDO;
  }
}
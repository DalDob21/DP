package cz.sa.ybus.server.infrastructure.provider.szdc.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;

import cz.sa.szdcPosition.api.Train;
import cz.sa.szdcPosition.api.TrainRequest;
import cz.sa.szdcPosition.api.TrainResponse;
import cz.sa.szdcInformationBoard.api.DepartureArrival;
import cz.sa.szdcInformationBoard.api.Design;
import cz.sa.szdcInformationBoard.api.RequestInformationPanel;
import cz.sa.szdcInformationBoard.api.RequestStationsList;
import cz.sa.szdcInformationBoard.api.ResponseInformationPanel;
import cz.sa.szdcInformationBoard.api.ResponseStationsList;
import cz.sa.szdcInformationBoard.api.Station;
import cz.sa.szdcInformationBoard.api.StationInfoPanelIdent;
import cz.sa.szdcInformationBoard.api.StatusList;
import cz.sa.szdcInformationBoard.api.View;
import cz.sa.ybus.core.szdc.service.dos.DesignDO;
import cz.sa.ybus.core.szdc.service.dos.StationDO;
import cz.sa.ybus.core.szdc.service.dos.TrainInformationInStationDO;
import cz.sa.ybus.core.szdc.service.dos.TrainPositionDO;
import cz.sa.ybus.core.szdc.service.dos.ViewDO;
import cz.sa.ybus.core.szdc.service.enums.DirectionEnum;
import cz.sa.ybus.server.infrastructure.provider.szdc.SzdcPositionWSApiService;
import cz.sa.ybus.server.infrastructure.provider.szdc.SzdcProvider;
import cz.sa.ybus.server.infrastructure.provider.szdc.SzdcInformationBoardWSApiService;
import cz.sa.ybus.server.infrastructure.provider.szdc.factory.PositionRequestFactory;
import cz.sa.ybus.server.infrastructure.provider.szdc.factory.InformationBoardRequestFactory;

@Service
public class SzdcProviderImpl implements SzdcProvider {

  @Autowired
  private SzdcInformationBoardWSApiService informationBoardService;
  @Autowired
  private InformationBoardRequestFactory inforamtionBoardRequest;

  @Autowired
  private SzdcPositionWSApiService positionService;
  @Autowired
  private PositionRequestFactory positionRequest;

  private final Logger log = LoggerFactory.getLogger(getClass());
  
  @Override
  public List<StationDO> getStations() {
    RequestStationsList request = inforamtionBoardRequest.createRequestStationsList();
    ResponseStationsList response = informationBoardService.getSoap().getStationList(request);
    if (response.getStatus() == StatusList.OK) {
      return response.getStation().stream().map(st -> convertStation(st)).collect(Collectors.toList());
    }
    log.error("StatusCode" + response.getStatus().value());
    throw new IllegalStateException(response.getStatus().value());
  }

  @Override
  public List<ViewDO> getViews(List<Integer> sr70numbers, DirectionEnum direction) {
    RequestInformationPanel request = inforamtionBoardRequest.createRequestInformationPanel();
    for (Integer sr70number : sr70numbers) {
      StationInfoPanelIdent ident = inforamtionBoardRequest.createStationInfoPanelIdent();
      ident.setSR70(sr70number);
      ident.setDeparture(toDepartureArrival(direction));
      request.getStationInfoPanelIdent().add(ident);
    }
    
    ResponseInformationPanel response = informationBoardService.getSoap().getInformationPanels(request);
    if (response.getStatus() == StatusList.OK) {
      return convertView(response);
    }
    log.error("StatusCode" + response.getStatus().value());
    throw new IllegalStateException(response.getStatus().value());
  }
  
  private DepartureArrival toDepartureArrival(DirectionEnum direction) {
    switch (direction) {
      case ARRIVAL:
        return DepartureArrival.ARRIVAL;
      case DEPARTURE:
        return DepartureArrival.DEPARTURE;
      case BOTH:
        return DepartureArrival.BOTH;
      default:
        throw new IllegalStateException("Unexpected direction: " + direction);
    }
  }

  @Override
  public List<TrainPositionDO> getTrainPositions() {
    TrainRequest request = positionRequest.createTrainRequest();
    TrainResponse response = positionService.getSoap().getTrainPosition(request);
    if (response.getStatus() == cz.sa.szdcPosition.api.StatusList.OK) {
      return response.getTrain().stream().map(train -> covnertTrain(train)).collect(Collectors.toList());
    }
    log.error("StatusCode" + response.getStatus().value());
    throw new IllegalStateException(response.getStatus().value());
  }
  
  /**
   * Konvertor pro Station
   * @param source
   * @return StationDO
   */
  private StationDO convertStation(Station source) {
    return new StationDO(source.getStationName(), source.getSR70());
  }

  /**
   * Konvertor pro Train
   * @param sourceTrain
   * @return TrainPositionDO
   */
  private TrainPositionDO covnertTrain(Train sourceTrain) {
    TrainPositionDO trainPosition = new TrainPositionDO();
    trainPosition.setType(sourceTrain.getType());
    trainPosition.setTrainNumber(sourceTrain.getNumber());
    trainPosition.setTrainName(sourceTrain.getName());
    trainPosition.setTrid(sourceTrain.getTRID());
    trainPosition.setComapanyName(sourceTrain.getRU().getRUName());
    trainPosition.setUicCode(Integer.parseInt(sourceTrain.getRU().getUICCode()));
    /*
    * SZDC pouziva dva ruzne formaty cislovani zastavek jeden u SzdcInformationBoardWSApiService (7 cislic) a druhy
    * u SzdcPositionWSApiService (8 cislic), my tyto dva formaty sjednocujem a pouzivame prvni z nich (7 cislic).
    */
    StationDO lPoint = new StationDO(sourceTrain.getLPoint().getStationName(), sourceTrain.getLPoint().getSR70() / 10);
    trainPosition.setLastPoint(lPoint);
    StationDO fPoint = new StationDO(sourceTrain.getFPoint().getStationName(), sourceTrain.getFPoint().getSR70() / 10);
    trainPosition.setFirstPoint(fPoint);
    StationDO lastConfirmedStation = new StationDO(sourceTrain.getLastConfirmedPoint().getStationName(),
        (sourceTrain.getLastConfirmedPoint().getSR70() / 10));
    trainPosition.setLastConfirmedPoint(lastConfirmedStation);
    trainPosition.setArrival(sourceTrain.getLastConfirmedPoint().isArrival());
    trainPosition.setReal(new DateTime(sourceTrain.getLastConfirmedPoint().getReal().toGregorianCalendar().getTime()));
    trainPosition.setPlanned(new DateTime(sourceTrain.getLastConfirmedPoint().getPlanned().toGregorianCalendar().getTime()));
    trainPosition.setDelay(sourceTrain.getLastConfirmedPoint().getDelay());
    trainPosition.setDiversion(sourceTrain.getLastConfirmedPoint().isDiversion());
    trainPosition.setSubstitution(sourceTrain.getLastConfirmedPoint().isSubstitution());
    return trainPosition;
  }

  /**
   * Konvertor pro View
   * @param response
   * @return List {@code <ViewDO>}
   */
  private List<ViewDO> convertView(ResponseInformationPanel response) {
    Preconditions.checkNotNull(response);
    List<ViewDO> viewDOs = new ArrayList<>();
    for (View v : response.getView()) {
      ViewDO viewDO = new ViewDO();
      viewDO.setSr70(v.getHead().getSR70());
      viewDO.setPl(v.getHead().isPl());
      viewDO.setTr(v.getHead().isTr());
      viewDO.setDesigns(convertDesigns(v.getDesign()));
      viewDOs.add(viewDO);
    }
    return viewDOs;
  }

  /**
   * Konvertor pro Design
   * @param designs
   * @return List {@code <DesignDO>}
   */
  private List<DesignDO> convertDesigns(List<Design> designs) {
    Preconditions.checkNotNull(designs);
    List<DesignDO> designDOs = new ArrayList<>();
    for (Design d : designs) {
      DesignDO designDO = new DesignDO();
      designDO.setDeparture(d.isDeparture());
      designDO.setValid(d.isValid());
      designDO.setMessage(convertToMessage(d.getTextOrTrain()));
      designDO.setTrainInformationInStations(convertToTrains(d.getTextOrTrain()));
      designDOs.add(designDO);
    }
    return designDOs;
  }

  /**
   * Konvertor pro message
   * @param textOrTrain
   * @return String
   */
  private String convertToMessage(List<Object> textOrTrain) {
    Preconditions.checkNotNull(textOrTrain);
    String message = "";
    for (Object o : textOrTrain) {
      if (o instanceof String) {
        return message= (String) o;
      }
    }
      return message;
  }
  
  /**
   * Konvertor pro Train
   * @param textOrTrain
   * @return List {@code <TrainInformationInStationDO>}
   */
  private List<TrainInformationInStationDO> convertToTrains(List<Object> textOrTrain) {
    Preconditions.checkNotNull(textOrTrain);
    List<TrainInformationInStationDO> trainInformationInStationDOs = new ArrayList<>();
    for (Object o : textOrTrain) {
      TrainInformationInStationDO trainInformationInStationDO = new TrainInformationInStationDO();
      if (o instanceof cz.sa.szdcInformationBoard.api.Train) {
        cz.sa.szdcInformationBoard.api.Train trainOnInformationBoard = (cz.sa.szdcInformationBoard.api.Train) o;
          
        trainInformationInStationDO.setTrainNumber(trainOnInformationBoard.getNumber());
        trainInformationInStationDO.setType(trainOnInformationBoard.getType());
        trainInformationInStationDO.setTrainName(trainOnInformationBoard.getName());
        trainInformationInStationDO.setCompany(trainOnInformationBoard.getCompany().getValue());
        trainInformationInStationDO.setTime(trainOnInformationBoard.getTime());
        trainInformationInStationDO.setLine(trainOnInformationBoard.getLine());
        trainInformationInStationDO.setDelay(trainOnInformationBoard.getDelay());
        trainInformationInStationDO.setDestination(trainOnInformationBoard.getDestination());
        List<String> directions = trainOnInformationBoard.getDirection().getStation();
        trainInformationInStationDO.setDirections(directions);
        trainInformationInStationDO.setPlatform(trainOnInformationBoard.getPlatform());
        trainInformationInStationDO.setTrack(trainOnInformationBoard.getTrack());
          
        trainInformationInStationDOs.add(trainInformationInStationDO);
      }
    }
    return trainInformationInStationDOs;
  }

}

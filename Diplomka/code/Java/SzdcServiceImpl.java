package cz.sa.ybus.core.szdc.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import cz.sa.ybus.core.common.service.AbstractService;
import cz.sa.ybus.core.szdc.service.dos.DesignDO;
import cz.sa.ybus.core.szdc.service.dos.StationDO;
import cz.sa.ybus.core.szdc.service.dos.TrainInformationInStationDO;
import cz.sa.ybus.core.szdc.service.dos.TrainPositionDO;
import cz.sa.ybus.core.szdc.service.dos.ViewDO;
import cz.sa.ybus.core.szdc.service.enums.DirectionEnum;
import cz.sa.ybus.domain.timetable.Country;
import cz.sa.ybus.server.domain.busdao.BusService;
import cz.sa.ybus.server.domain.property.Property;
import cz.sa.ybus.server.domain.property.PropertyRepository;
import cz.sa.ybus.server.domain.user.User;
import cz.sa.ybus.server.domain.user.UserRepository;
import cz.sa.ybus.server.infrastructure.dao.szdc.SzdcDao;
import cz.sa.ybus.server.infrastructure.db.entity.TimetableUtils;
import cz.sa.ybus.server.infrastructure.db.entity.bus.BusConnectionVO;
import cz.sa.ybus.server.infrastructure.db.entity.bus.BusStationVO;
import cz.sa.ybus.server.infrastructure.db.entity.timetable.StationVO;
import cz.sa.ybus.server.infrastructure.provider.szdc.SzdcProvider;
import cz.sa.ybus.server.infrastructure.util.MailType;
import cz.sa.ybus.server.infrastructure.util.Mailer;
import cz.sa.ybus.server.logging.LogBefore;

@Service
public class SzdcServiceImpl extends AbstractService implements SzdcService {
  @Autowired
  private SzdcProvider szdcProvider;
  @Autowired
  transient private BusService busService;
  @Autowired
  private SzdcDao szdcDao;
  @Autowired
  transient private Mailer mailer;
  @Autowired
  transient private PropertyRepository propertyRepository;
  @Autowired
  private UserRepository userRepository;
  
  private Map<Integer, LocalDate> errorStationSendedEmail = Maps.newHashMap();
  
  /**
   * Vsechna Sr70 cisla zastavek, pres ktera jezdi RJ_CZ vlaky, ale uz jsou mimo YBUS a mimo nase klienty.
   * Napr. PrahaSmichov, kde se vlak pouze otaci.
   * 54572263, 54583666, 54572289, 54581173<br>
   * SZDC pouziva dva ruzne formaty cislovani zastavek jeden u SzdcInformationBoardWSApiService (7 cislic) a druhy
   * u SzdcPositionWSApiService (8 cislic), my tyto dva formaty sjednocujem a pouzivame prvni z nich (7 cislic).
   */
  private static final Set<Integer> EXTERNAL_SR70_NUMBERS = ImmutableSet.of(5457226, 5458366, 5457228, 5458117);
  
  @Override
  @LogBefore
  public void updatePlatforms() {
    List<TrainStationInfo> stationInfos = getCurrentDepartingStationsInfo();

    /**
     * z listu stationInfos je potreba vyselektovat pro uceli dotazu do SZDC
     * cisla zastavek sr70number a odstranit duplicity
     */
    List<Integer> sr70numbers = stationInfos.stream().map(sr70 -> sr70.getSr70Number()).distinct().collect(Collectors.toList());

    List<ViewDO> viewDOs = szdcProvider.getViews(sr70numbers, DirectionEnum.BOTH);
    
    if (viewDOs.size() != sr70numbers.size()) {
      List<Integer> sr70numbersFromViewDOs = viewDOs.stream().map(viewDO -> viewDO.getSr70()).collect(Collectors.toList());
      List<TrainStationInfo> unavailableStations = stationInfos.stream().filter(si -> !sr70numbersFromViewDOs.contains(si.getSr70Number())).collect(Collectors.toList());
      sendErrorMail(unavailableStations);
    }

    Map<Long, String> platformInfosForUpdate = new HashMap<>();

    for (ViewDO viewDO : viewDOs) {
      /**
       * Pokud SZDC zobrazuje v zastavce na informcacni tabuli nastupiste potom Pl = true
       */
      Boolean pl = viewDO.getPl();
      /**
       * Pokud SZDC zobrazuje v zastavce na informcacni tabuli kolej potom Tr = true
       */
      Boolean tr = viewDO.getTr();
      for (DesignDO designDO : viewDO.getDesigns()) {
        if (designDO.getValid()) {
          for (TrainInformationInStationDO trainInformationInStationDO : designDO.getTrainInformationInStations()) {
            String trainNumber = trainInformationInStationDO.getTrainNumber();
            for (TrainStationInfo stationInfo : stationInfos) {
              if (trainNumber.equals(stationInfo.getTrainNumber())
                  && (viewDO.getSr70().intValue() == stationInfo.getSr70Number().intValue())) {
                StrBuilder platformInfo = new StrBuilder();
                String platform = trainInformationInStationDO.getPlatform();
                String track = trainInformationInStationDO.getTrack();
                platformInfo.append(pl && platform.length() > 0 ? platform : "-");
                platformInfo.append("/");
                platformInfo.append(tr && track.length() > 0 ? track : "-");
                platformInfosForUpdate.put(stationInfo.getBusStationId(), platformInfo.toString());
              }
            }
          }
        }
        else {
          log.error("Valid" + designDO.getValid());
        }
      }
    }
    for (Map.Entry<Long, String> entry : platformInfosForUpdate.entrySet()) {
      busService.updateBusStationPlatform(entry.getKey(), entry.getValue());
    }
  }

  private List<TrainStationInfo> getCurrentDepartingStationsInfo() {

    List<TrainStationInfo> trainStationInfos = new ArrayList<>();

    List<BusStationVO> busStationVOs = szdcDao.getCurrentBusStations();
    for (BusStationVO busStationVO : busStationVOs) {
      String connectionCode = busStationVO.getBusConnection().getConnectionCode(); // z DB se nam vrati 'RJ 1001' a potrebujem pouze '1001'
      Pattern pattern = Pattern.compile("(\\d+)");
      Matcher m = pattern.matcher(connectionCode);
      String code = null;
      while (m.find()) {
        code = m.group();
      }
      if (code!=null) {
        TrainStationInfo tsi = new TrainStationInfo();
        tsi.setBusStationId(busStationVO.getId());
        tsi.setSr70Number(convertSR70toSzdcFormat(busStationVO.getStation().getSr70Number()));
        tsi.setTrainNumber(code);
        tsi.setStationName(busStationVO.getStation().getCity()+" "+busStationVO.getStation().getName());
        trainStationInfos.add(tsi);
      }
    }
    return trainStationInfos;
  }

  /**
   * V YBUS DB mame ulozene cisla sr70 ve tvaru, ktery SZDC nepodporuje ale
   * pomoci vhodne konverze jej lze upravit na tvar, ktery SZDC uz prijme
   * 
   * @param Long
   *          sr70number
   * @return Integer sr70number
   */
  private Integer convertSR70toSzdcFormat(Long sr70number) {
    String uicCountryCode = Country.CZ.getUicCountryCode();
    return Integer.valueOf(uicCountryCode + String.valueOf(sr70number / 10));
  }

  private static class TrainStationInfo {
    private Integer sr70Number;
    private Long busStationId;
    private String trainNumber;
    private String stationName;

    public String getStationName() {
      return stationName;
    }

    public void setStationName(String stationName) {
      this.stationName = stationName;
    }

    public Integer getSr70Number() {
      return sr70Number;
    }

    public void setSr70Number(Integer sr70Number) {
      this.sr70Number = sr70Number;
    }

    public Long getBusStationId() {
      return busStationId;
    }

    public void setBusStationId(Long busStationId) {
      this.busStationId = busStationId;
    }

    public String getTrainNumber() {
      return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
      this.trainNumber = trainNumber;
    }

  }

  @Override
  @LogBefore
  public void updateDelays() {
    
    /**
     * Vypnuti/zapnuti nacitani zpozdeni od szdc
     * do logu se vypiseje kdy byla sluzba vynuta a kym
     */
    Property property = propertyRepository.getProperty("szdc.updateDelayEnable");
    if (!property.getBooleanValue()) {
      User user = userRepository.getUser(property.getUpdatedByUserId());
      log.info("Aktualizace zpozdeni je docasne vypnuta od : " + property.getDateUpdated() + ", uzivatelem : " + user.getLogin());
      return;
    }

    Map<Long, TrainPositionDO> delayInfosForUpdate = new HashMap<>();

    List<BusConnectionVO> busConnectionVOs = szdcDao.getTodayBusConnections();

    List<TrainPositionDO> trainPositionDOs = szdcProvider.getTrainPositions();
    for (TrainPositionDO trainPositionDO : trainPositionDOs) {
      for (BusConnectionVO busConnectionVO : busConnectionVOs) {
        if (isBusConnectionMatching(busConnectionVO, trainPositionDO)) {
          delayInfosForUpdate.put(busConnectionVO.getId(), trainPositionDO);
        }
      }
    }
    for (Map.Entry<Long, TrainPositionDO> entry : delayInfosForUpdate.entrySet()) {
      final Long busConnectionId = entry.getKey();
      final TrainPositionDO trainPosition = entry.getValue();
      final Integer delay = trainPosition == null ? null : trainPosition.getDelay();
      final StationDO lastConfirmedPoint = trainPosition == null ? null : trainPosition.getLastConfirmedPoint();
      final String stationName = lastConfirmedPoint == null ? null : lastConfirmedPoint.getStationName();
      final String messageInfo = BusService.SZDC_DELAY_INFO + " (" + StringUtils.stripAccents(stationName) + ")";
      final Integer sr70 = lastConfirmedPoint == null ? null : lastConfirmedPoint.getSr70();
      busService.updateBusConnectionDelay(busConnectionId, delay, messageInfo, sr70);
    }

  }

  /**
   * Pro porovani cisel vlaku musime upravit SZDC cislo na format vhodny pro YBUS
   * @param trainNumber
   * @return String
   */
  private String convertSzdcTrainNumberToYbus(Integer trainNumber) {
    return "RJ " + trainNumber;
  }

  /**
   * Posle mail o chybe informacni tabule v zastavkach na prislusnou adresu jednou za den
   * @param unavailableStations
   */
  private void sendErrorMail(List<TrainStationInfo> unavailableStations) {
    LocalDate today = LocalDate.now();
    //vsechny zaznamy, ktere jsou uz den stare budou promazany
    errorStationSendedEmail.values().removeIf(oldValue -> oldValue.isBefore(today));
    String subject = "SZDC: Nedostupne informace (data) z informacni tabule";
    String emails = propertyRepository.getProperty("szdc.errorMailTo").getValue();
    for (TrainStationInfo us : unavailableStations) {
      LocalDate lastSentDate = errorStationSendedEmail.get(us.getSr70Number().intValue());
      //kontrola jestli byl uz dnes poslan email
      if (Objects.equals(today, lastSentDate)) {
        continue;
      }
      String msg = "K informacni tabuli v zastavce: "+us.getStationName()+" , SZDC kod ( "+us.getSr70Number()+" ) nemame opravneni k cteni dat";
      mailer.sendMultiMail(MailType.INTERNAL, null, emails, subject, msg, msg);
      errorStationSendedEmail.put(us.getSr70Number(), today);
    }
  }
  
  private boolean isBusConnectionMatching(BusConnectionVO busConnectionVO, TrainPositionDO trainPositionDO) {
    String trainNumber = convertSzdcTrainNumberToYbus(trainPositionDO.getTrainNumber());
    if (!busConnectionVO.getConnectionCode().equals(trainNumber)) {
      return false;
    }
    if (Objects.equals(trainPositionDO.getLastConfirmedPoint().getSr70(), busConnectionVO.getLastPostionSzdc())) {
      return false;
    }
  
    // Vlak muze jet i mimo trasu definovanou Ybusem. (Napr. zajet do tocny v PrahaSmichov.) Tam uz ale neprevazi klienty a tj. uz nas nezajima zpozdeni.
    return isPositionAtYbusRoute(busConnectionVO, trainPositionDO);
  }
  
  // Pozn. Metoda je naimplementovana na miru pro (k zari 2017) jedinou vlakovou trasu Praha-Havirov (pripadne protazenou dal),
  // na ktere nam SZDC poskytuje info. o zpozdeni.
  private boolean isPositionAtYbusRoute(BusConnectionVO busConnectionVO, TrainPositionDO trainPositionDO) {
    if (EXTERNAL_SR70_NUMBERS.contains(trainPositionDO.getLastConfirmedPoint().getSr70())) {
      return false;
    }

    StationVO firstStationVO = TimetableUtils.getFirstBusStation(busConnectionVO.getBusStations()).getStation();
    StationVO lastStationVO = TimetableUtils.getLastBusStation(busConnectionVO.getBusStations()).getStation();

    if (isMatchingPosition(trainPositionDO, firstStationVO)) {
      return !trainPositionDO.getArrival();
    }
    if (isMatchingPosition(trainPositionDO, lastStationVO)) {
      return trainPositionDO.getArrival();
    }
    return true;
  }
  
  private boolean isMatchingPosition(TrainPositionDO trainPositionDO, StationVO stationVO) {
    // SZDC mame jen v Cesku
    if (stationVO.getCity().getCountry() != Country.CZ) {
      return false;
    }
    return (Objects.equals(trainPositionDO.getLastConfirmedPoint().getSr70(), convertSR70toSzdcFormat(stationVO.getSr70Number())));
  }  

}

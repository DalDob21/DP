package cz.sa.ybus.server.infrastructure.provider.trafficcontrol.impl;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cz.sa.ybus.core.common.annotations.NotNull;
import cz.sa.ybus.core.trafficcontrol.service.dos.TrainCompositionDO;
import cz.sa.ybus.core.trafficcontrol.service.dos.WagonDO;
import cz.sa.ybus.server.infrastructure.provider.trafficcontrol.ExcelToCsvConverter;
import cz.sa.ybus.server.infrastructure.provider.trafficcontrol.TrafficControlMailDownloader;
import cz.sa.ybus.server.infrastructure.provider.trafficcontrol.TrafficControlProvider;

/**
 * 
 * @author dalibor.dobes
 *
 */
@Component
public class TrafficControlProviderImpl implements TrafficControlProvider {

  @Autowired
  private TrafficControlMailDownloader mailDownloader;
  @Autowired
  private ExcelToCsvConverter excelToCsvConverter;
  
  public static final char WAGON_SEPARATOR = ',';
  public static final char DEFAULT_SEPARATOR = ';';
  
  private static final Pattern TRAIN_CODE_PATTERN = Pattern.compile("[0-9]+");
  
  public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");
  
  private final Logger log = LoggerFactory.getLogger(getClass());
  
  @Override
  @NotNull
  public void updateTrainCompositions(@NotNull final Consumer<TrainCompositionDO> trainCompositionUpdater) {

    final byte[] attachmentBytes = mailDownloader.downloadEmailAttachments();
    if (attachmentBytes != null) {
      final String csvString = excelToCsvConverter.convertTrainStructure(attachmentBytes);
      if (csvString != null) {
        try (final CSVParser csvParser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT.withDelimiter(DEFAULT_SEPARATOR));
        ) {
          for (CSVRecord csvRecord : csvParser.getRecords()) {
            final String csvDate = csvRecord.get(0);
            final String csvTrainCode = csvRecord.get(1);
            final String csvTrainStructure = csvRecord.get(2);

            final TrainCompositionDO tcDO = new TrainCompositionDO();
            tcDO.setLocalDate(LocalDate.parse(csvDate, DATE_TIME_FORMATTER));
            if (!TRAIN_CODE_PATTERN.matcher(csvTrainCode).matches()) {
              log.error("The train number must not contain letters");
              continue;
            }
            final Integer trainCode = Integer.parseInt(csvTrainCode);
            tcDO.setTrainName(trainCode);
            tcDO.setWagons(getTrainStructure(csvTrainStructure, trainCode));
            trainCompositionUpdater.accept(tcDO);
          }
        }
        catch (final UnsupportedEncodingException e) {
          log.error("UnsupportedEncoding for CSV file");
        }
        catch (final IOException e) {
          log.error("Error while parsing CSV", e);
        }
      }
    }
  }
  
  @NotNull
  private List<WagonDO> getTrainStructure(@NotNull final String csvTrainStructure, @NotNull final Integer trainName) {
    
    final List<WagonDO> wagons = new ArrayList<>();
    final StringTokenizer stringTokenizer = new StringTokenizer(csvTrainStructure, String.valueOf(WAGON_SEPARATOR));
    int position = 1;
    while (stringTokenizer.hasMoreElements()) {
      final String wagonId = stringTokenizer.nextToken();
      if (!wagonId.isEmpty()) {
        if (!Character.isDigit(wagonId.charAt(0))) {
          final WagonDO wDO = new WagonDO();
          wDO.setPositionInTrain(position);
          wDO.setWagonId(wagonId);
          wagons.add(wDO);
          position++;
        }
      }
    }
    if (trainName % 2 != 0) {
      int countOfWagons = wagons.size();
      final int controlValue = wagons.size() + 1;
      Collections.sort(wagons, Comparator.comparingInt(WagonDO::getPositionInTrain));
      for (final WagonDO wagonDO : wagons) {
        if ((wagonDO.getPositionInTrain() + countOfWagons) == controlValue) {
          wagonDO.setPositionInTrain(countOfWagons);
          countOfWagons--;
        }
      }
    }
    return wagons;
  }
}
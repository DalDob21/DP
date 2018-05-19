package cz.sa.ybus.server.infrastructure.provider.trafficcontrol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.annotation.Nullable;

import org.apache.commons.lang3.text.StrBuilder;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cz.sa.ybus.core.common.annotations.NotNull;
import cz.sa.ybus.server.infrastructure.provider.trafficcontrol.impl.TrafficControlProviderImpl;

/**
 * Trida napsana namiru nacitani struktury vlaku z excelu, po vytvoreni dispecerskeho systemu bude jiz nepotrebna a prijde odstranit 
 * @author dalibor.dobes
 *
 */
@Component
public class ExcelToCsvConverter {

  /* nazev listu v excelu */
  private static final String XLS_TRAIN_STRUCTURE_SHEET_NAME = "Struktura";

  /* list Struktura */
  private static final int XLS_STRUCTURE_FIRST_ROW_NUM = 1;
  private static final int XLS_STRUCTURE_FIRST_CELL_NUM = 0;
  private static final int XLS_STRUCTURE_FIRST_WAGON_CELL_NUM = 2;
  private static final int XLS_STRUCTURE_LAST_WAGON_CELL_NUM = 16;

   /* oddelovace */
  private static final String CSV_ROW_DELIMITER = "\n";
  private static final String STRING_WRAPPER = "\"";

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Nullable
  public String convertTrainStructure(@NotNull final byte[] attachmentBytes) {

    String csvString = null;

    try {

      final StrBuilder csvBuilder = new StrBuilder();

      /* nacist excel */
      try (final InputStream inputXls = new ByteArrayInputStream(attachmentBytes)) {

        /* otevrit dany list v excelu */
        final XSSFSheet xlsSheet = new XSSFWorkbook(inputXls).getSheet(XLS_TRAIN_STRUCTURE_SHEET_NAME);

        XSSFRow xlsRow = null;

        for (int row = XLS_STRUCTURE_FIRST_ROW_NUM; row < xlsSheet.getLastRowNum(); row++) {
          xlsRow = xlsSheet.getRow(row);
          final StrBuilder csvRowBuilder = new StrBuilder();

          XSSFCell xlsCell = null;

          /* bunka na pozici "0" je vzdy datum */
          xlsCell = xlsRow.getCell(XLS_STRUCTURE_FIRST_CELL_NUM);
          if (DateUtil.isCellDateFormatted(xlsCell)) {
            final Date cDate = xlsCell.getDateCellValue();
            if (cDate == null) {
              continue;
            }
            final LocalDate lDate = new LocalDate(cDate);
            final String cellDate = lDate.toString(TrafficControlProviderImpl.DATE_TIME_FORMATTER);
            csvRowBuilder.append(getWrappedString(cellDate));
            csvRowBuilder.append(TrafficControlProviderImpl.DEFAULT_SEPARATOR);
          }

          /* bunka na pozici "1" je nazev vlaku */
          xlsCell = xlsRow.getCell(XLS_STRUCTURE_FIRST_CELL_NUM + 1);
          final String cellTrain = xlsCell.getRawValue();
          if (cellTrain == null) {
            continue;
          }
          csvRowBuilder.append(getWrappedString(cellTrain));
          csvRowBuilder.append(TrafficControlProviderImpl.DEFAULT_SEPARATOR);
          csvRowBuilder.append(STRING_WRAPPER);

          /* nacitani kodu vagonu do jednoho stringu v CSV */
          for (int cellWagons = XLS_STRUCTURE_FIRST_WAGON_CELL_NUM; cellWagons < XLS_STRUCTURE_LAST_WAGON_CELL_NUM + 1; cellWagons++) {
            xlsCell = xlsRow.getCell(cellWagons);
            final String wagonCellValue = xlsCell.getRawValue();
            csvRowBuilder.append(wagonCellValue);
            csvRowBuilder.append(TrafficControlProviderImpl.WAGON_SEPARATOR);
          }
          csvRowBuilder.append(STRING_WRAPPER);
          csvRowBuilder.append(CSV_ROW_DELIMITER);
          csvBuilder.append(csvRowBuilder);
        }
      }
      csvString = csvBuilder.toString();
    }
    catch (IOException e) {
      log.error("Cannot convert Excel file to CSV");
    }
    return csvString;
  }

  private String getWrappedString(@NotNull final String string) {
    return STRING_WRAPPER + string + STRING_WRAPPER;
  }
}
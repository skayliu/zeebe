package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
public class ExportService {
  @Autowired
  private ReportService reportService;

  @Autowired
  private ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(getClass());

  private byte[] writeRawDataToBytes(
      Map<String, Long> result,
      GroupByDto groupBy,
      ViewDto view,
      Integer limit,
      Integer offset
  ) {

    List<String[]> csvStrings = CSVUtils.map(result, limit, offset);

    String[] header = new String [2];
    header[0] = groupBy.toString();
    header[1] = view.getOperation() + "_" + view.getEntity() + "_" + view.getProperty();
    csvStrings.add(0, header);
    byte[] bytes = getCSVBytes(csvStrings);

    return bytes;
  }

  public byte[] writeRawDataToBytes(List<RawDataProcessInstanceDto> rawData, Integer limit, Integer offset) {
    List<String[]> csvStrings = CSVUtils.map(rawData, limit, offset);

    byte[] bytes = getCSVBytes(csvStrings);

    return bytes;
  }

  public byte[] getCSVBytes(List<String[]> csvStrings) {
    ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    BufferedWriter bufferedWriter = new BufferedWriter(
        new OutputStreamWriter(arrayOutputStream));
    CSVWriter csvWriter = new CSVWriter(bufferedWriter);
    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (Exception e) {
      logger.error("can't write CSV to buffer", e);
    }
    return bytes;
  }

  public byte[] getCSVForReport(String userId, String reportId, Integer limit, Integer offset) {

    Optional<ReportResultDto> reportResultDto = Optional.empty();
    try {
      reportResultDto = Optional.ofNullable(reportService.evaluateSavedReportWithAuthorizationCheck(userId, reportId));
    } catch (Exception e) {
      logger.error("can't evaluate report",e);
    }

    byte[] result = reportResultDto.map((reportResult) -> {
      byte[] bytes = null;
      if (reportResult.getClass().equals(RawDataReportResultDto.class)) {
        RawDataReportResultDto cast = (RawDataReportResultDto) reportResult;
        bytes = this.writeRawDataToBytes(
            cast.getResult(),
            limit,
            offset
        );

      } else if (reportResult.getClass().equals(MapReportResultDto.class)) {
        MapReportResultDto cast = (MapReportResultDto) reportResult;
        bytes = this.writeRawDataToBytes(
            cast.getResult(),
            reportResult.getData().getGroupBy(),
            reportResult.getData().getView(),
            limit,
            offset
        );
      }
      return bytes;
    }).orElse(null);

    return result;
  }


  public byte[] writeRawDataToBytes(List<RawDataProcessInstanceDto> toMap) {
    return this.writeRawDataToBytes(toMap, null, null);
  }

  public byte[] getCSVForReport(String userId, String reportId) {
    return this.getCSVForReport(
      userId,
      reportId,
      configurationService.getExportCsvLimit(),
      configurationService.getExportCsvOffset()
    );
  }
}

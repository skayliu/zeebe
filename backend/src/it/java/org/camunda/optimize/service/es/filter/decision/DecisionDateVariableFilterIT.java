/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRelativeDateInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingDateInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionDateVariableFilterIT extends AbstractDecisionDefinitionIT {

  @BeforeEach
  public void setup() {
    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2019-06-15T12:00:00+02:00"));
  }

  @Test
  public void resultFilterByGreaterThanDateInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(200.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, dateTimeInputFilterStart, null
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2019-06-06T00:00:00")
    );
  }

  @Test
  public void resultFilterByLessThanDateInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterEnd = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(200.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, null, dateTimeInputFilterEnd
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2018-01-01T00:00:00")
    );
  }

  @Test
  public void resultFilterByDateRangeInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final OffsetDateTime dateTimeInputFilterEnd = OffsetDateTime.parse("2019-02-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(200.0, "2019-01-01T01:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(300.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, dateTimeInputFilterStart, dateTimeInputFilterEnd
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2019-01-01T01:00:00")
    );
  }

  @Test
  public void resultFilterByRelativeDateInputVariable() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, toDateString(now))
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, toDateString(now.minusDays(2)))
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, toDateString(now.minusDays(3)))
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRelativeDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, 1L, DateFilterUnit.DAYS
    )));
    RawDataDecisionReportResultDto result1 = reportClient.evaluateRawReport(reportData).getResult();

    reportData.setFilter(Lists.newArrayList(createRelativeDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, 3L, DateFilterUnit.DAYS
    )));
    RawDataDecisionReportResultDto result2 = reportClient.evaluateRawReport(reportData).getResult();

    // then
    Assertions.assertThat(result1.getInstanceCount()).isEqualTo(1L);
    Assertions.assertThat(result2.getInstanceCount()).isEqualTo(3L);
  }

  @Test
  public void resultFilterByRollingDateInputVariable() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, toDateString(now))
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRollingDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, 0L, DateFilterUnit.DAYS
    )));
    RawDataDecisionReportResultDto result1 = reportClient.evaluateRawReport(reportData).getResult();

    // now move the day
    LocalDateUtil.setCurrentTime(now.plusDays(1L));
    final RawDataDecisionReportResultDto result2 = reportClient.evaluateRawReport(reportData).getResult();

    reportData.setFilter(Lists.newArrayList(createRollingDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, 1L, DateFilterUnit.DAYS
    )));
    RawDataDecisionReportResultDto result3 = reportClient.evaluateRawReport(reportData).getResult();

    // then
    Assertions.assertThat(result1.getInstanceCount()).isEqualTo(1L);
    Assertions.assertThat(result2.getInstanceCount()).isEqualTo(0L);
    Assertions.assertThat(result3.getInstanceCount()).isEqualTo(1L);
  }

  private String toDateString(final OffsetDateTime now) {
    return now.format(embeddedOptimizeExtension.getDateTimeFormatter());
  }

  private DecisionReportDataDto createReportWithAllVersion(DecisionDefinitionEngineDto decisionDefinitionDto) {
    return DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }

}

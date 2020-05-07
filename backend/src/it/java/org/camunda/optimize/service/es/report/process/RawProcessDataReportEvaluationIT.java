/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

public class RawProcessDataReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getDuration(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getBusinessKey(), is(BUSINESS_KEY));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(reportData.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getDuration(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationForRunningProcessInstance() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleUserTaskProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(reportData.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(nullValue()));
    assertThat(rawDataProcessInstanceDto.getDuration(), is(nullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationByIdForOneProcessInstance() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReportById(reportId);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getDuration(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationWithSeveralProcessInstances() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(result.getIsComplete(), is(true));
    Set<String> expectedProcessInstanceIds = new HashSet<>();
    expectedProcessInstanceIds.add(processInstance.getId());
    expectedProcessInstanceIds.add(processInstance2.getId());
    for (RawDataProcessInstanceDto rawDataProcessInstanceDto : result.getData()) {
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
      String actualProcessInstanceId = rawDataProcessInstanceDto.getProcessInstanceId();
      assertThat(expectedProcessInstanceIds.contains(actualProcessInstanceId), is(true));
      expectedProcessInstanceIds.remove(actualProcessInstanceId);
    }
  }

  @Test
  public void reportEvaluationOnProcessInstanceWithAllVariableTypes() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "Hello World!");
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 2);
    variables.put("longVar", "Hello World!");
    variables.put("dateVar", new Date());

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    rawDataProcessInstanceDto.getVariables().
      forEach((varName, varValue) -> {
                assertThat(variables.keySet().contains(varName), is(true));
                assertThat(variables.get(varName), is(notNullValue()));
              }
      );
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processKey)
      .processDefinitionVersions(Collections.singletonList(ALL_VERSIONS))
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .build();
    reportData.setTenantIds(selectedTenants);
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
    result.getData().forEach(rawDataDecisionInstanceDto -> assertThat(
      rawDataDecisionInstanceDto.getTenantId(),
      isOneOf(selectedTenants.toArray())
    ));
  }

  @Test
  public void resultShouldBeOrderAccordingToStartDate() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseExtension.changeProcessInstanceStartDate(
      processInstanceDto2.getId(),
      OffsetDateTime.now().minusDays(2)
    );
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseExtension.changeProcessInstanceStartDate(
      processInstanceDto3.getId(),
      OffsetDateTime.now().minusDays(1)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(rawDataList, isInDescendingOrdering());
  }

  private Matcher<? super List<RawDataProcessInstanceDto>> isInDescendingOrdering() {
    return new TypeSafeMatcher<List<RawDataProcessInstanceDto>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("The given list should be sorted in ascending order!");
      }

      @Override
      protected boolean matchesSafely(List<RawDataProcessInstanceDto> items) {
        for (int i = (items.size() - 1); i > 0; i--) {
          if (items.get(i).getStartDate().isAfter(items.get(i - 1).getStartDate())) {
            return false;
          }
        }
        return true;
      }
    };
  }

  @Test
  public void testCustomOrderOnProcessInstancePropertyIsApplied() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcess();

    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId());

    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId());

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final Object[] processInstanceIdsOrderedAsc = newArrayList(
      processInstanceDto1.getId(), processInstanceDto2.getId(), processInstanceDto3.getId()
    ).stream().sorted(Collections.reverseOrder()).toArray();

    // when
    ProcessReportDataDto reportData = createReport(processInstanceDto1);
    reportData.getConfiguration().setSorting(new SortingDto(ProcessInstanceIndex.PROCESS_INSTANCE_ID, SortOrder.DESC));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(
      rawDataList.stream().map(RawDataProcessInstanceDto::getProcessInstanceId).collect(Collectors.toList()),
      contains(processInstanceIdsOrderedAsc)
    );
  }

  @Test
  public void testInvalidSortPropertyNameSilentlyIgnored() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstanceDto1);
    reportData.getConfiguration().setSorting(new SortingDto("lalalala", SortOrder.ASC));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(rawDataList.size(), is(1));
  }

  @Test
  public void testCustomOrderOnProcessInstanceVariableIsApplied() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcessWithVariables(
      ImmutableMap.of("intVar", 0)
    );

    ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processInstanceDto1.getDefinitionId(), ImmutableMap.of("intVar", 2)
    );

    ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processInstanceDto1.getDefinitionId(), ImmutableMap.of("intVar", 1)
    );

    ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processInstanceDto1.getDefinitionId(), ImmutableMap.of("otherVar", 0)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final Object[] processInstanceIdsOrderedAsc = newArrayList(
      processInstanceDto1.getId(), processInstanceDto2.getId(), processInstanceDto3.getId(), processInstanceDto4.getId()
    ).toArray();

    // when
    ProcessReportDataDto reportData = createReport(processInstanceDto1);
    reportData.getConfiguration().setSorting(new SortingDto("variable:intVar", SortOrder.ASC));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(
      rawDataList.stream().map(RawDataProcessInstanceDto::getProcessInstanceId).collect(Collectors.toList()),
      contains(processInstanceIdsOrderedAsc)
    );
  }

  @Test
  public void testInvalidSortVariableNameSilentlyIgnored() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstanceDto1);
    reportData.getConfiguration().setSorting(new SortingDto("variable:lalalala", SortOrder.ASC));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(rawDataList.size(), is(1));
  }

  @Test
  public void variablesOfOneProcessInstanceAreAddedToOther() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("varName1", "value1");

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    variables.clear();
    variables.put("varName2", "value2");
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    result.getData().forEach(
      rawDataProcessInstanceDto1 -> {
        Map<String, Object> vars = rawDataProcessInstanceDto1.getVariables();
        assertThat(vars.keySet().size(), is(2));
        assertThat(vars.values().contains(""), is(true));
        // ensure is ordered
        List<String> actual = new ArrayList<>(vars.keySet());
        List<String> expected = new ArrayList<>(vars.keySet());
        Collections.sort(expected);
        assertThat(actual, contains(expected.toArray()));
      }
    );
  }

  @Test
  public void evaluationReturnsOnlyDataToGivenProcessDefinitionId() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
  }

  //test that basic support for filter is there
  @Test
  public void durationFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit(DurationFilterUnit.DAYS)
                           .value((long) 1)
                           .operator(">")
                           .add()
                           .buildList());
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult1 =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result1 = evaluationResult1.getResult();

    // then
    final ProcessReportDataDto resultDataDto1 = evaluationResult1.getReportDefinition().getData();
    assertThat(resultDataDto1.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto1.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto1.getView(), is(notNullValue()));
    assertThat(resultDataDto1.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result1.getData(), is(notNullValue()));
    assertThat(result1.getData().size(), is(0));

    // when
    reportData = createReport(processInstance);
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit(DurationFilterUnit.DAYS)
                           .value((long) 1)
                           .operator("<")
                           .add()
                           .buildList());

    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult2 =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result2 = evaluationResult2.getResult();

    // then
    final ProcessReportDataDto resultDataDto2 = evaluationResult2.getReportDefinition().getData();
    assertThat(resultDataDto2.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto2.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto2.getView(), is(notNullValue()));
    assertThat(resultDataDto2.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result2.getData(), is(notNullValue()));
    assertThat(result2.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result2.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime past = engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult1 =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result1 = evaluationResult1.getResult();

    // then
    final ProcessReportDataDto resultDataDto1 = evaluationResult1.getReportDefinition().getData();
    assertThat(resultDataDto1.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto1.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto1.getView(), is(notNullValue()));
    assertThat(resultDataDto1.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result1.getData(), is(notNullValue()));
    assertThat(result1.getData().size(), is(0));

    // when
    reportData = createReport(processInstance);
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult2 =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result2 = evaluationResult2.getResult();

    // then
    final ProcessReportDataDto resultDataDto2 = evaluationResult2.getReportDefinition().getData();
    assertThat(resultDataDto2.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto2.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto2.getView(), is(notNullValue()));
    assertThat(resultDataDto2.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result2.getData(), is(notNullValue()));
    assertThat(result2.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result2.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void variableFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance);
    reportData.setFilter(createVariableFilter());
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  private List<ProcessFilterDto<?>> createVariableFilter() {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto("var", true);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    );
    variables.put("goToTask1", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processDefinition.getKey())
      .processDefinitionVersions(Collections.singletonList(processDefinition.getVersionAsString()))
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .build();


    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();

    reportData.getFilter().addAll(flowNodeFilter);
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      reportClient.evaluateRawReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    //when
    Response response = reportClient.evaluateReportAndReturnResponse((ProcessReportDataDto) null);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void missingProcessDefinition() {

    //when
    ProcessReportDataDto dataDto = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(null)
      .processDefinitionVersions(null)
      .viewEntity(null)
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .visualization(ProcessVisualization.TABLE)
      .build();

    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void missingViewField() {
    //when
    ProcessReportDataDto dataDto = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(null)
      .processDefinitionVersions(null)
      .viewEntity(null)
      .viewProperty(null)
      .visualization(ProcessVisualization.TABLE)
      .build();
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void missingPropertyField() {
    //when
    ProcessReportDataDto dataDto = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(null)
      .processDefinitionVersions(null)
      .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
      .viewProperty(null)
      .visualization(ProcessVisualization.TABLE)
      .build();
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void missingVisualizationField() {
    //when
    ProcessReportDataDto dataDto = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(null)
      .processDefinitionVersions(null)
      .viewEntity(null)
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .visualization(null)
      .build();

    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    ProcessReportDataDto reportData = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processDefinitionKey)
      .processDefinitionVersions(Collections.singletonList(processDefinitionVersion))
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .visualization(ProcessVisualization.TABLE)
      .build();

    return createNewReport(reportData);
  }

  private ProcessReportDataDto createReport(ProcessInstanceEngineDto processInstance) {
    return new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processInstance.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(processInstance.getProcessDefinitionVersion()))
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .visualization(ProcessVisualization.TABLE)
      .build();
  }
}

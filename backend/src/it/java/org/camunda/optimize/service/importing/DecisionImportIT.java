/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler.DECISION_DEFINITION_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class DecisionImportIT extends AbstractImportIT {

  private static final Set<String> DECISION_DEFINITION_NULLABLE_FIELDS =
    Collections.singleton(DecisionDefinitionIndex.TENANT_ID);

  @Test
  public void importOfDecisionDataCanBeDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setImportDmnDataEnabled(false);
    embeddedOptimizeExtension.reloadConfiguration();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    BpmnModelInstance exampleProcess = Bpmn.createExecutableProcess().name("foo").startEvent().endEvent().done();
    engineIntegrationExtension.deployAndStartProcess(exampleProcess);

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 1L);
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_DEFINITION_INDEX_NAME, 1L);
  }

  @Test
  public void importOfDecisionDefinition_dataIsImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // given
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);

    // given failed ES update requests to store new definition
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest definitionImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + DECISION_DEFINITION_INDEX_NAME + "\""));
    esMockServer
      .when(definitionImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then the definition will be stored when update next works
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 1L);
    esMockServer.verify(definitionImportMatcher);
  }

  @Test
  public void importOfDecisionDInstance_dataIsImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // given
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 0L);

    // given failed ES update requests to store new instance
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest instanceImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + DECISION_INSTANCE_INDEX_NAME + "\""));
    esMockServer
      .when(instanceImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then the instance will be stored when update next works
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 1L);
    esMockServer.verify(instanceImportMatcher);
  }

  @Test
  public void allDecisionDefinitionFieldDataIsAvailable() {
    //given
    engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.deployDecisionDefinition();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllDataWithCount(
      DECISION_DEFINITION_INDEX_NAME,
      2L,
      DECISION_DEFINITION_NULLABLE_FIELDS
    );
  }

  @Test
  public void importsAllDefinitionsEvenIfTotalAmountIsAboveMaxPageSize() {
    //given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionDefinitionMaxPageSize(1);
    engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.deployDecisionDefinition();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getDecisionDefinitionCount()).isEqualTo(2L);

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getDecisionDefinitionCount()).isEqualTo(3L);
  }

  @Test
  public void decisionDefinitionTenantIdIsImportedIfPresent() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    engineIntegrationExtension.deployDecisionDefinitionWithTenant(tenantId);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(DecisionDefinitionIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void decisionDefinitionDefaultEngineTenantIdIsApplied() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    engineIntegrationExtension.deployDecisionDefinition();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(DecisionDefinitionIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void decisionDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    engineIntegrationExtension.deployDecisionDefinitionWithTenant(expectedTenantId);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(DecisionDefinitionIndex.TENANT_ID)).isEqualTo(expectedTenantId);
  }

  @Test
  public void decisionInstanceFieldDataIsAvailable() {
    //given
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);

    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertDecisionInstanceFieldSetAsExpected(hit);
  }

  @Test
  public void decisionInstanceTenantIdIsImportedIfPresent() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    final DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinitionWithTenant(
        tenantId);
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(DecisionInstanceIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void decisionInstanceDefaultEngineTenantIdIsApplied() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(DecisionInstanceIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void decisionInstanceEngineTenantIdIsPreferredOverDefaultTenantId() {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    final DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinitionWithTenant(
        expectedTenantId);
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(DecisionInstanceIndex.TENANT_ID)).isEqualTo(expectedTenantId);
  }

  @Test
  public void multipleDecisionInstancesAreImported() {
    //given
    DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionEngineDto.getId());

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 2L);
  }

  @Test
  public void decisionImportIndexesAreStored() throws IOException {
    // given
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse searchDecisionInstanceTimestampBasedIndexResponse = getDecisionInstanceIndexResponse();
    assertThat(searchDecisionInstanceTimestampBasedIndexResponse.getHits().getTotalHits().value).isEqualTo(1L);
    final TimestampBasedImportIndexDto decisionInstanceDto = parseToDto(
      searchDecisionInstanceTimestampBasedIndexResponse.getHits().getHits()[0], TimestampBasedImportIndexDto.class
    );
    assertThat(decisionInstanceDto.getTimestampOfLastEntity()).isBefore(OffsetDateTime.now());

    SearchResponse searchDecisionDefinitionIndexResponse = getDecisionDefinitionIndexById();
    assertThat(searchDecisionDefinitionIndexResponse.getHits().getTotalHits().value).isEqualTo(1L);
    final TimestampBasedImportIndexDto definitionImportIndex = parseToDto(
      searchDecisionDefinitionIndexResponse.getHits().getHits()[0],
      TimestampBasedImportIndexDto.class
    );
    assertThat(definitionImportIndex.getEsTypeIndexRefersTo()).isEqualTo(DECISION_DEFINITION_IMPORT_INDEX_DOC_ID);
  }

  @Test
  public void importMoreThanOnePage() {
    // given
    int originalMaxPageSize = embeddedOptimizeExtension.getConfigurationService()
      .getEngineImportProcessInstanceMaxPageSize();
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionInstanceMaxPageSize(1);
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 2L);
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionInstanceMaxPageSize(originalMaxPageSize);
  }

  @Test
  public void definitionImportWorksEvenIfDeploymentRequestFails() {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    engineMockServer
      .when(requestMatcher, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    // when
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    List<DecisionDefinitionOptimizeDto> decisionDefinitions = definitionClient.getAllDecisionDefinitions();
    assertThat(decisionDefinitions).hasSize(1);
  }

  private SearchResponse getDecisionDefinitionIndexById() throws IOException {
    final String decisionDefinitionIndexId = DECISION_DEFINITION_IMPORT_INDEX_DOC_ID + "-1";
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery("_id", decisionDefinitionIndexId))
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(searchRequest, RequestOptions.DEFAULT);
  }

  private SearchResponse getDecisionInstanceIndexResponse() throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery(ES_TYPE_INDEX_REFERS_TO, DECISION_INSTANCE_INDEX_NAME))
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(searchRequest, RequestOptions.DEFAULT);
  }


  private <T> T parseToDto(final SearchHit searchHit, Class<T> dtoClass) {
    try {
      return elasticSearchIntegrationTestExtension.getObjectMapper().readValue(searchHit.getSourceAsString(), dtoClass);
    } catch (IOException e) {
      throw new RuntimeException("Failed parsing dto: " + dtoClass.getSimpleName());
    }
  }

  public void allEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                            final long count) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(elasticsearchIndex);

    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(count);
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      // in this test suite we only care about decision types, no asserts besides count on others
      if (DECISION_INSTANCE_INDEX_NAME.equals(elasticsearchIndex)) {
        assertDecisionInstanceFieldSetAsExpected(searchHit);
      } else if (DECISION_DEFINITION_INDEX_NAME.equals(elasticsearchIndex)) {
        assertAllFieldsSet(DECISION_DEFINITION_NULLABLE_FIELDS, searchHit);
      }
    }
  }

  private long getDecisionDefinitionCount() {
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    return idsResp.getHits().getTotalHits().value;
  }

  private void assertDecisionInstanceFieldSetAsExpected(final SearchHit hit) {
    final DecisionInstanceDto dto = parseToDto(hit, DecisionInstanceDto.class);
    assertThat(dto.getProcessDefinitionId()).isNull();
    assertThat(dto.getProcessDefinitionKey()).isNull();
    assertThat(dto.getDecisionDefinitionId()).isNotNull();
    assertThat(dto.getDecisionDefinitionKey()).isNotNull();
    assertThat(dto.getDecisionDefinitionVersion()).isNotNull();
    assertThat(dto.getEvaluationDateTime()).isNotNull();
    assertThat(dto.getProcessInstanceId()).isNull();
    assertThat(dto.getRootProcessInstanceId()).isNull();
    assertThat(dto.getActivityId()).isNull();
    assertThat(dto.getCollectResultValue()).isNull();
    assertThat(dto.getRootDecisionInstanceId()).isNull();
    assertThat(dto.getInputs().size()).isEqualTo(2);
    dto.getInputs().forEach(inputInstanceDto -> {
      assertThat(inputInstanceDto.getId()).isNotNull();
      assertThat(inputInstanceDto.getClauseId()).isNotNull();
      assertThat(inputInstanceDto.getClauseName()).isNotNull();
      assertThat(inputInstanceDto.getType()).isNotNull();
      assertThat(inputInstanceDto.getValue()).isNotNull();
    });
    assertThat(dto.getOutputs().size()).isEqualTo(2);
    dto.getOutputs().forEach(outputInstanceDto -> {
      assertThat(outputInstanceDto.getId()).isNotNull();
      assertThat(outputInstanceDto.getClauseId()).isNotNull();
      assertThat(outputInstanceDto.getClauseName()).isNotNull();
      assertThat(outputInstanceDto.getType()).isNotNull();
      assertThat(outputInstanceDto.getValue()).isNotNull();
      assertThat(outputInstanceDto.getRuleId()).isNotNull();
      assertThat(outputInstanceDto.getRuleOrder()).isNotNull();
    });
    assertThat(dto.getEngine()).isNotNull();
    assertThat(dto.getTenantId()).isNull();
  }

}

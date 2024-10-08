/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.DIGEST;
import static io.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.ENABLED;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.ProcessRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessRepositoryES implements ProcessRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(
      final Set<String> processDefinitionKeys) {
    log.debug("Fetching process overviews for [{}] processes", processDefinitionKeys.size());
    if (processDefinitionKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
        .query(QueryBuilders.idsQuery().addIds(processDefinitionKeys.toArray(new String[0])))
        .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(PROCESS_OVERVIEW_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason =
          String.format(
              "Was not able to fetch overviews for processes [%s].", processDefinitionKeys);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
            searchResponse.getHits(), ProcessOverviewDto.class, objectMapper)
        .stream()
        .collect(
            Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }

  @Override
  public Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey() {
    log.debug("Fetching all available process overviews.");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
        .query(boolQuery().must(termQuery(DIGEST + "." + ENABLED, true)))
        .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(PROCESS_OVERVIEW_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = "Was not able to fetch process overviews.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
            searchResponse.getHits(), ProcessOverviewDto.class, objectMapper)
        .stream()
        .collect(
            Collectors.toMap(
                ProcessOverviewDto::getProcessDefinitionKey, ProcessOverviewDto::getDigest));
  }

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData() {
    log.debug("Fetching pending process overviews");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
        .query(
            QueryBuilders.prefixQuery(
                ProcessOverviewDto.Fields.processDefinitionKey, "pendingauthcheck"))
        .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(PROCESS_OVERVIEW_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch pending processes";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
            searchResponse.getHits(), ProcessOverviewDto.class, objectMapper)
        .stream()
        .collect(
            Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }
}

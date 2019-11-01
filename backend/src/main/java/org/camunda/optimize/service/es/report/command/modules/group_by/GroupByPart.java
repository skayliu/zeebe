/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


public abstract class GroupByPart<Data extends SingleReportDataDto> {

  @Setter
  protected DistributedByPart<Data> distributedByPart;

  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<Data> context) {
    distributedByPart.adjustSearchRequest(searchRequest, baseQuery, context);
  }

  public abstract List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                             final ExecutionContext<Data> context);

  public String generateCommandKey(final Supplier<Data> createNewDataDto) {
    final Data dataForCommandKey = createNewDataDto.get();
    addGroupByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    distributedByPart.addDistributedByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    return dataForCommandKey.createCommandKey();
  }

  public abstract CompositeCommandResult retrieveQueryResult(SearchResponse response,
                                                             final ExecutionContext<Data> executionContext);

  public boolean getSortByKeyIsOfNumericType(final ExecutionContext<Data> context) {
    return false;
  }

  public Optional<SortingDto> getSorting(final ExecutionContext<Data> context) {
    return context.getReportConfiguration().getSorting();
  }

  protected abstract void addGroupByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);

}

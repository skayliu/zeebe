/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByNone extends ProcessGroupByPart {

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    // nothing to do here, since we don't group so just pass the view part on
    return Stream.of(distributedByPart.createAggregation(context))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(final SearchResponse response,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    CompositeCommandResult compositeCommandResult = new CompositeCommandResult();

    final List<DistributedByResult> distributions =
      distributedByPart.retrieveResult(response, response.getAggregations(), context);
    GroupByResult groupByResult = GroupByResult.createEmptyGroupBy(distributions);
    compositeCommandResult.setGroup(groupByResult);
    return compositeCommandResult;
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new NoneGroupByDto());
  }

  @Override
  public Optional<SortingDto> getSorting(final ExecutionContext<ProcessReportDataDto> context) {
    return Optional.empty();
  }
}

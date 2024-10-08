/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.index.reindex.BulkByScrollTask;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class TaskRepositoryES implements TaskRepository {
  private final OptimizeElasticsearchClient esClient;

  @Override
  public List<TaskRepository.TaskProgressInfo> tasksProgress(final String action) {
    ListTasksRequest request = new ListTasksRequest().setActions(action).setDetailed(true);
    return safe(
        () ->
            esClient.getTaskList(request).getTasks().stream()
                .filter(taskInfo -> taskInfo.getStatus() instanceof BulkByScrollTask.Status)
                .map(taskInfo -> (BulkByScrollTask.Status) taskInfo.getStatus())
                .map(
                    status ->
                        new TaskProgressInfo(
                            getProgress(status), status.getTotal(), getProcessedTasksCount(status)))
                .toList(),
        e -> "Failed to fetch task progress from Elasticsearch!",
        log);
  }

  private static long getProcessedTasksCount(BulkByScrollTask.Status status) {
    return status.getDeleted() + status.getCreated() + status.getUpdated();
  }

  private static int getProgress(BulkByScrollTask.Status status) {
    return status.getTotal() > 0
        ? Double.valueOf((double) getProcessedTasksCount(status) / status.getTotal() * 100.0D)
            .intValue()
        : 0;
  }
}

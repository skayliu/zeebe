/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.SemanticVersion;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class IndexSchemaValidatorElasticSearch implements IndexSchemaValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IndexSchemaValidatorElasticSearch.class);

  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  @Autowired Set<IndexDescriptor> indexDescriptors;

  @Autowired TasklistProperties tasklistProperties;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  private Set<String> getAllIndexNamesForIndex(String index) {
    final String indexPattern = String.format("%s-%s*", getIndexPrefix(), index);
    LOGGER.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = retryElasticsearchClient.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = String.format("%s-%s-\\d.*", getIndexPrefix(), index);
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  private String getIndexPrefix() {
    return tasklistProperties.getElasticsearch().getIndexPrefix();
  }

  public Set<String> newerVersionsForIndex(IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<String> olderVersionsForIndex(IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }

  private Set<String> versionsForIndex(IndexDescriptor indexDescriptor) {
    final Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor.getIndexName());
    return allIndexNames.stream()
        .map(this::getVersionFromIndexName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Optional<String> getVersionFromIndexName(String indexName) {
    final Matcher matcher = VERSION_PATTERN.matcher(indexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  @Override
  public void validate() {
    if (!hasAnyTasklistIndices()) {
      return;
    }
    final Set<String> errors = new HashSet<>();
    indexDescriptors.forEach(
        indexDescriptor -> {
          final Set<String> oldVersions = olderVersionsForIndex(indexDescriptor);
          final Set<String> newerVersions = newerVersionsForIndex(indexDescriptor);
          if (oldVersions.size() > 1) {
            errors.add(
                String.format(
                    "More than one older version for %s (%s) found: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), oldVersions));
          }
          if (!newerVersions.isEmpty()) {
            errors.add(
                String.format(
                    "Newer version(s) for %s (%s) already exists: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), newerVersions));
          }
        });
    if (!errors.isEmpty()) {
      throw new TasklistRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  @Override
  public boolean hasAnyTasklistIndices() {
    final Set<String> indices =
        retryElasticsearchClient.getIndexNames(
            tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
    return !indices.isEmpty();
  }

  @Override
  public boolean schemaExists() {
    try {
      final Set<String> indices =
          retryElasticsearchClient.getIndexNames(
              tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      final List<String> allIndexNames =
          map(indexDescriptors, IndexDescriptor::getFullQualifiedName);

      final Set<String> aliases =
          retryElasticsearchClient.getAliasesNames(
              tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      final List<String> allAliasesNames = map(indexDescriptors, IndexDescriptor::getAlias);

      return indices.containsAll(allIndexNames)
          && aliases.containsAll(allAliasesNames)
          && validateNumberOfReplicas(allIndexNames);
    } catch (Exception e) {
      LOGGER.error("Check for existing schema failed", e);
      return false;
    }
  }

  public boolean validateNumberOfReplicas(final List<String> indexes) {
    for (String index : indexes) {
      final Map<String, String> response =
          retryElasticsearchClient.getIndexSettingsFor(
              index, RetryElasticsearchClient.NUMBERS_OF_REPLICA);
      if (!response
          .get(RetryElasticsearchClient.NUMBERS_OF_REPLICA)
          .equals(String.valueOf(tasklistProperties.getElasticsearch().getNumberOfReplicas()))) {
        return false;
      }
    }
    return true;
  }
}

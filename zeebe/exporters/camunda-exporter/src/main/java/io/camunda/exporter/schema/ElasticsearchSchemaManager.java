/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.config.ElasticsearchProperties;
import io.camunda.exporter.config.ElasticsearchProperties.IndexSettings;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSchemaManager implements SchemaManager {
  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);
  private final SearchEngineClient elasticsearchClient;
  private final List<IndexDescriptor> indexDescriptors;
  private final List<IndexTemplateDescriptor> indexTemplateDescriptors;
  private final ElasticsearchProperties elasticsearchProperties;

  public ElasticsearchSchemaManager(
      final SearchEngineClient elasticsearchClient,
      final List<IndexDescriptor> indexDescriptors,
      final List<IndexTemplateDescriptor> indexTemplateDescriptors,
      final ElasticsearchProperties elasticsearchProperties) {
    this.elasticsearchClient = elasticsearchClient;
    this.indexDescriptors = indexDescriptors;
    this.indexTemplateDescriptors = indexTemplateDescriptors;
    this.elasticsearchProperties = elasticsearchProperties;
  }

  @Override
  public void initialiseResources() {
    indexTemplateDescriptors.forEach(this::createIndexTemplate);
    indexDescriptors.forEach(elasticsearchClient::createIndex);
  }

  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final var newFieldEntry : newFields.entrySet()) {
      final var descriptor = newFieldEntry.getKey();
      final var newProperties = newFieldEntry.getValue();

      if (descriptor instanceof IndexTemplateDescriptor) {
        LOG.info("Updating template: {}", ((IndexTemplateDescriptor) descriptor).getTemplateName());
        createIndexTemplate((IndexTemplateDescriptor) descriptor);
      } else {
        LOG.info(
            "Index alias: {}. New fields will be added {}", descriptor.getAlias(), newProperties);

        elasticsearchClient.putMapping(descriptor, newProperties);
      }
    }
  }

  private void createIndexTemplate(final IndexTemplateDescriptor templateDescriptor) {
    final var templateReplicas =
        elasticsearchProperties
            .getReplicasByIndexName()
            .getOrDefault(
                templateDescriptor.getIndexName(),
                elasticsearchProperties.getDefaultSettings().getNumberOfReplicas());
    final var templateShards =
        elasticsearchProperties
            .getShardsByIndexName()
            .getOrDefault(
                templateDescriptor.getIndexName(),
                elasticsearchProperties.getDefaultSettings().getNumberOfShards());

    final var settings = new IndexSettings();
    settings.setNumberOfShards(templateShards);
    settings.setNumberOfReplicas(templateReplicas);

    elasticsearchClient.createIndexTemplate(templateDescriptor, settings, false);
  }
}

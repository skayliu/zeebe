/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Optional;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;

public class RequestOptionsProvider {

  private final ConfigurationService configurationService;

  public RequestOptionsProvider() {
    this(null);
  }

  public RequestOptionsProvider(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public RequestOptions getRequestOptions() {
    final RequestOptions.Builder optionsBuilder = RequestOptions.DEFAULT.toBuilder();
    Optional.ofNullable(configurationService)
        .ifPresent(
            config ->
                optionsBuilder.setHttpAsyncResponseConsumerFactory(
                    new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(
                        getElasticsearchResponseConsumerBufferLimitInBytes())));
    return optionsBuilder.build();
  }

  private int getElasticsearchResponseConsumerBufferLimitInBytes() {
    return configurationService.getElasticSearchConfiguration().getResponseConsumerBufferLimitInMb()
        * 1024
        * 1024;
  }
}

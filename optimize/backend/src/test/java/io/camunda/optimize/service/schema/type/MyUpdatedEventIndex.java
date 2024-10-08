/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.schema.type;

import static io.camunda.optimize.service.db.schema.index.MetadataIndex.SCHEMA_VERSION;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.xcontent.XContentBuilder;

@Slf4j
public abstract class MyUpdatedEventIndex<TBuilder> implements IndexMappingCreator<TBuilder> {

  public static final String MY_NEW_FIELD = "myAwesomeNewField";

  @Override
  public String getIndexName() {
    return DatabaseConstants.METADATA_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
    try {
      // @formatter:off
      XContentBuilder content =
          jsonBuilder()
              .startObject()
              .startObject("properties")
              .startObject(SCHEMA_VERSION)
              .field("type", "keyword")
              .endObject()
              .startObject(MY_NEW_FIELD)
              .field("type", "keyword")
              .endObject()
              .endObject()
              .endObject();
      source = content;
      // @formatter:on
    } catch (IOException e) {
      String message = "Could not add mapping for type '" + getIndexName() + "'!";
      log.error(message, e);
    }
    return source;
  }
}

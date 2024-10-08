/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.SettingsIndex.LAST_MODIFIED;
import static io.camunda.optimize.service.db.schema.index.SettingsIndex.SHARING_ENABLED;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.index.SettingsIndex;
import io.camunda.optimize.service.db.writer.SettingsWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(ElasticSearchCondition.class)
public class SettingsWriterES implements SettingsWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public void upsertSettings(final SettingsDto settingsDto) {
    log.debug("Writing settings to ES");

    try {
      final UpdateRequest request = createSettingsUpsert(settingsDto);
      esClient.update(request);
    } catch (IOException e) {
      final String errorMessage = "There were errors while writing settings.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private UpdateRequest createSettingsUpsert(final SettingsDto settingsDto)
      throws JsonProcessingException {
    Set<String> fieldsToUpdate = new HashSet<>();
    if (settingsDto.getSharingEnabled().isPresent()) {
      fieldsToUpdate.add(SHARING_ENABLED);
    }
    if (!fieldsToUpdate.isEmpty()) {
      // This always gets updated
      fieldsToUpdate.add(LAST_MODIFIED);
    } else {
      throw new BadRequestException("No settings can be updated, as no values are present!");
    }

    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(fieldsToUpdate, settingsDto, objectMapper);

    return new UpdateRequest()
        .index(SETTINGS_INDEX_NAME)
        .id(SettingsIndex.ID)
        .upsert(objectMapper.writeValueAsString(settingsDto), XContentType.JSON)
        .script(updateScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }
}

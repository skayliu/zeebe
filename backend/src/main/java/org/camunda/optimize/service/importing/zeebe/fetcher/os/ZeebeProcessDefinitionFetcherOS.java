/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.importing.zeebe.db.ZeebeProcessDefinitionFetcher;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME;

@Component
@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Conditional(OpenSearchCondition.class)
public class ZeebeProcessDefinitionFetcherOS
  extends AbstractZeebeRecordFetcherOS<ZeebeProcessDefinitionRecordDto> implements ZeebeProcessDefinitionFetcher {

  public ZeebeProcessDefinitionFetcherOS(final int partitionId,
                                         final OptimizeOpenSearchClient osClient,
                                         final ObjectMapper objectMapper,
                                         final ConfigurationService configurationService) {
    super(partitionId, osClient, objectMapper, configurationService);
  }

  @Override
  protected String getBaseIndexName() {
    return ZEEBE_PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  protected Class<ZeebeProcessDefinitionRecordDto> getRecordDtoClass() {
    return ZeebeProcessDefinitionRecordDto.class;
  }

}

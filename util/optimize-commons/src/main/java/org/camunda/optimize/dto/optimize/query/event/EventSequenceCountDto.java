/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class EventSequenceCountDto implements OptimizeDto {

  String id;
  EventTypeDto sourceEvent;
  EventTypeDto targetEvent;
  Long count;

}

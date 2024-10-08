/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;

public interface ReadonlyStreamProcessorContext {

  ProcessingScheduleService getScheduleService();

  /**
   * Returns the partition ID
   *
   * @return partition ID
   */
  int getPartitionId();

  /**
   * @return true when scheduled tasks should run async, concurrently to the processing actor.
   */
  boolean enableAsyncScheduledTasks();

  StreamClock getClock();
}

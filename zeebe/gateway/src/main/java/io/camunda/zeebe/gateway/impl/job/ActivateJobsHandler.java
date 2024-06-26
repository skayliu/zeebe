/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.scheduler.ActorControl;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Can handle an 'activate jobs' request from a client. */
public interface ActivateJobsHandler extends Consumer<ActorControl> {

  static final AtomicLong ACTIVATE_JOBS_REQUEST_ID_GENERATOR = new AtomicLong(1);

  /**
   * Handle activate jobs request from a client
   *
   * @param request The request to handle
   * @param responseObserver The stream to write the responses to
   */
  void activateJobs(
      BrokerActivateJobsRequest request,
      ServerStreamObserver<ActivateJobsResponse> responseObserver,
      final long requestTimeout);

  public static InflightActivateJobsRequest toInflightActivateJobsRequest(
      final BrokerActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver,
      final long longPollingTimeout) {
    return new InflightActivateJobsRequest(
        ACTIVATE_JOBS_REQUEST_ID_GENERATOR.getAndIncrement(),
        request,
        responseObserver,
        longPollingTimeout);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backpressure;

public final class NoopRequestLimiter<ContextT> implements RequestLimiter<ContextT> {

  @Override
  public boolean tryAcquire(final int streamId, final long requestId, final ContextT context) {
    return true;
  }

  @Override
  public void onResponse(final int streamId, final long requestId) {}

  @Override
  public void onIgnore(final int streamId, final long requestId) {}

  @Override
  public int getLimit() {
    return 0;
  }

  @Override
  public int getInflightCount() {
    return 0;
  }
}

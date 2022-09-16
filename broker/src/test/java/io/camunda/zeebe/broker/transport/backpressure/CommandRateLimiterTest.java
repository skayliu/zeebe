/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.limit.SettableLimit;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CommandRateLimiterTest {

  private static final int INITIAL_LIMIT = 5;
  private final SettableLimit limit = new SettableLimit(INITIAL_LIMIT);

  private final CommandRateLimiter rateLimiter = CommandRateLimiter.builder().limit(limit).build(0);
  private final Intent context = ProcessInstanceCreationIntent.CREATE;

  @Test
  void shouldAcquire() {
    assertThat(rateLimiter.tryAcquire(0, 1, context)).isTrue();
  }

  @Test
  void shouldNotAcquireAfterLimit() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, 1, context)).isTrue());
    // then
    assertThat(rateLimiter.tryAcquire(0, 1, context)).isFalse();
  }

  @Test
  void shouldCompleteRequestOnResponse() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, i, context)));
    assertThat(rateLimiter.tryAcquire(0, 100, context)).isFalse();

    // when
    rateLimiter.onResponse(0, 0);

    // then
    assertThat(rateLimiter.tryAcquire(0, 100, context)).isTrue();
  }

  @Test
  void shouldCompleteAllRequests() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, i, context)));
    assertThat(rateLimiter.tryAcquire(0, 100, context)).isFalse();

    // when
    IntStream.range(0, limit.getLimit()).forEach(i -> rateLimiter.onResponse(0, i));

    // then
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, i, context)).isTrue());
    assertThat(rateLimiter.tryAcquire(0, 100, context)).isFalse();
  }

  @Test
  void shouldAcquireWhenJobCompleteCommandAfterLimit() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, 1, context)).isTrue());

    // then
    assertThat(rateLimiter.tryAcquire(0, 1, JobIntent.COMPLETE)).isTrue();
  }

  @Test
  void shouldAcquireWhenJobFailCommandAfterLimit() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, 1, context)).isTrue());

    // then
    assertThat(rateLimiter.tryAcquire(0, 1, JobIntent.FAIL)).isTrue();
  }

  @Test
  void shouldReleaseRequestOnIgnore() {
    // given
    rateLimiter.tryAcquire(0, 1, context);
    assertThat(rateLimiter.getInflightCount()).isEqualTo(1);

    // when
    rateLimiter.onIgnore(0, 1);

    // then
    assertThat(rateLimiter.getInflightCount()).isEqualTo(0);
  }
}

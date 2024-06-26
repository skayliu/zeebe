/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg.LimitAlgorithm;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class FlowControlCfgTest {
  public final Map<String, String> environment = new HashMap<>();

  @Test
  void shouldHaveAppendLimitByDefault() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var appendLimit = cfg.getFlowControl().getAppend();

    // then
    assertThat(appendLimit.isEnabled()).isTrue();
    assertThat(appendLimit.useWindowed()).isFalse();
    assertThat(appendLimit.getAlgorithm()).isEqualTo(LimitAlgorithm.LEGACY_VEGAS);
  }

  @Test
  void shouldSetAppendLimit() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("flowcontrol-cfg", environment);
    final var appendLimit = cfg.getFlowControl().getAppend();

    // then
    assertThat(appendLimit.isEnabled()).isTrue();
    assertThat(appendLimit.useWindowed()).isFalse();
    assertThat(appendLimit.getAlgorithm()).isEqualTo(LimitAlgorithm.GRADIENT);
  }
}

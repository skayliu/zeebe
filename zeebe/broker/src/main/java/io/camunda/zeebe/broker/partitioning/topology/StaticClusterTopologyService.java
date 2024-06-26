/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public class StaticClusterTopologyService implements ClusterTopologyService {

  private PartitionDistribution partitionDistribution;

  @Override
  public PartitionDistribution getPartitionDistribution() {
    return partitionDistribution;
  }

  @Override
  public void registerPartitionChangeExecutor(final PartitionChangeExecutor executor) {
    // do nothing. Static cluster topology cannot be changed.
  }

  @Override
  public void removePartitionChangeExecutor() {
    // do nothing. Static cluster topology cannot be changed.
  }

  @Override
  public ActorFuture<Void> start(final BrokerStartupContext brokerStartupContext) {
    try {
      final BrokerCfg brokerConfiguration = brokerStartupContext.getBrokerConfiguration();
      final var localMember =
          brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();

      final var staticConfiguration =
          StaticConfigurationGenerator.getStaticConfiguration(brokerConfiguration, localMember);

      partitionDistribution =
          new PartitionDistribution(staticConfiguration.generatePartitionDistribution());
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public void registerTopologyChangeListener(final InconsistentConfigurationListener listener) {
    // do nothing. Static cluster topology cannot be changed.
  }

  @Override
  public void removeTopologyChangeListener() {
    // do nothing. Static cluster topology cannot be changed.
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    partitionDistribution = null;
    return CompletableActorFuture.completed(null);
  }
}

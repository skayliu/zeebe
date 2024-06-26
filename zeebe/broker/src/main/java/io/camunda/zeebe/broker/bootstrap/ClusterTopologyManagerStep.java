/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.topology.ClusterTopologyService;
import io.camunda.zeebe.broker.partitioning.topology.DynamicClusterTopologyService;
import io.camunda.zeebe.broker.partitioning.topology.StaticClusterTopologyService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class ClusterTopologyManagerStep
    implements io.camunda.zeebe.scheduler.startup.StartupStep<BrokerStartupContext> {

  @Override
  public String getName() {
    return "Cluster Topology Manager";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> started =
        brokerStartupContext.getConcurrencyControl().createFuture();

    final ClusterTopologyService clusterTopologyService =
        getClusterTopologyService(brokerStartupContext.getBrokerConfiguration());
    clusterTopologyService
        .start(brokerStartupContext)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                brokerStartupContext.setClusterTopology(clusterTopologyService);
                started.complete(brokerStartupContext);
              } else {
                started.completeExceptionally(error);
              }
            });

    return started;
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> stopFuture =
        brokerStartupContext.getConcurrencyControl().createFuture();
    final var clusterTopologyService = brokerStartupContext.getClusterTopology();
    if (clusterTopologyService != null) {
      clusterTopologyService
          .closeAsync()
          .onComplete(
              (ignore, error) -> {
                if (error == null) {
                  brokerStartupContext.setClusterTopology(null);
                  stopFuture.complete(brokerStartupContext);
                } else {
                  stopFuture.completeExceptionally(error);
                }
              });
    } else {
      stopFuture.complete(brokerStartupContext);
    }
    return stopFuture;
  }

  private static ClusterTopologyService getClusterTopologyService(
      final BrokerCfg brokerConfiguration) {
    return brokerConfiguration.getExperimental().getFeatures().isEnableDynamicClusterTopology()
        ? new DynamicClusterTopologyService()
        : new StaticClusterTopologyService();
  }
}

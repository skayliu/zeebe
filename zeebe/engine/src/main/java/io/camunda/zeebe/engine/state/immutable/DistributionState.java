/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import java.util.Optional;

public interface DistributionState {

  /**
   * Returns whether there are any retriable distributions for a given key.
   *
   * @param distributionKey the key of the distribution
   * @return true if there are retriable distributions for the given key, otherwise false
   */
  boolean hasRetriableDistribution(long distributionKey);

  /**
   * Returns whether there are any pending distributions for a given key.
   *
   * @param distributionKey the key of the distribution
   * @return true if there are pending distributions for the given key, otherwise false
   */
  boolean hasPendingDistribution(long distributionKey);

  /**
   * Returns whether a specific distribution for a specific partition is retriable.
   *
   * @param distributionKey the key of the distribution that may be retriable
   * @param partition the id of the partition for which the distribution might be retriable
   * @return {@code true} if the specific retriable distribution exists, otherwise {@code false}.
   */
  boolean hasRetriableDistribution(long distributionKey, int partition);

  /**
   * Returns whether a specific distribution for a specific partition is pending.
   *
   * @param distributionKey the key of the distribution that may be pending
   * @param partition the id of the partition for which the distribution might be pending
   * @return {@code true} if the specific pending distribution exists, otherwise {@code false}.
   */
  boolean hasPendingDistribution(long distributionKey, int partition);

  /**
   * Returns the {@link CommandDistributionRecord} for the given distribution key. This method takes
   * a partition id. This is only used to set the partition property in the {@link
   * CommandDistributionRecord}. Doing so allows us to return a whole record, without the need to
   * remember setting the partition everytime this method is called.
   *
   * @param distributionKey the key of the distribution
   * @param partition the partition to distribute to
   * @return an new instance of the {@link CommandDistributionRecord}
   */
  CommandDistributionRecord getCommandDistributionRecord(long distributionKey, int partition);

  /**
   * Visits each persisted retriable distribution, providing both the key of that distribution and
   * the {@link CommandDistributionRecord}.
   *
   * <p>Note that a new instance of the record is provided for each visit, so the visitor does not
   * have to make a copy when long term access is needed.
   *
   * @param visitor Each retriable distribution is visited by this visitor
   */
  void foreachRetriableDistribution(PendingDistributionVisitor visitor);

  /**
   * Returns the distribution key at the head of the queue for the given partition.
   *
   * @param queue the queue to look up
   * @param partition the partition id within the queue
   * @return the distribution key at the head of the queues or an empty optional if there is no
   *     queued distribution for that queue and partition.
   */
  Optional<Long> getNextQueuedDistributionKey(String queue, int partition);

  /**
   * Returns the queue for the given distribution or an empty optional if this distribution was not
   * queued.
   */
  Optional<String> getQueueIdForDistribution(long distributionKey);

  /** This visitor can visit pending distributions of {@link CommandDistributionRecord}. */
  @FunctionalInterface
  interface PendingDistributionVisitor {

    /**
     * Visits a pending distribution.
     *
     * @param distributionKey The key of the pending distribution
     * @param pendingDistribution The pending distribution itself as command distribution record
     */
    void visit(final long distributionKey, final CommandDistributionRecord pendingDistribution);
  }
}

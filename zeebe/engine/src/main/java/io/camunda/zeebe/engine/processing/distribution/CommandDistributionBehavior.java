/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The network communication between the partitions is unreliable. To allow communication between
 * partitions in a reliable way, we've built command distribution: a way for partitions to send,
 * receive, and acknowledge (or retry sending) commands between partitions.
 *
 * <p>This behavior allows distributing a command to other partitions, and for those receiving
 * partitions to acknowledge the distributed commands back to the partition that started.
 *
 * @see <a
 *     href="https://github.com/camunda/camunda/blob/main/zeebe/docs/generalized_distribution.md">
 *     generalized_distribution.md</a>
 */
public final class CommandDistributionBehavior {

  private final DistributionState distributionState;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final RoutingInfo routingInfo;
  private final InterPartitionCommandSender interPartitionCommandSender;

  private final int currentPartitionId;

  // Records are expensive to construct, so we create them once and reuse them
  private final CommandDistributionRecord commandDistributionStarted =
      new CommandDistributionRecord();
  private final CommandDistributionRecord commandDistributionDistributing =
      new CommandDistributionRecord();
  private final CommandDistributionRecord commandDistributionEnqueued =
      new CommandDistributionRecord();
  private final CommandDistributionRecord commandDistributionAcknowledge =
      new CommandDistributionRecord();

  public CommandDistributionBehavior(
      final DistributionState distributionState,
      final Writers writers,
      final int currentPartition,
      final RoutingInfo routingInfo,
      final InterPartitionCommandSender partitionCommandSender) {
    this.distributionState = distributionState;
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    this.routingInfo = routingInfo;
    interPartitionCommandSender = partitionCommandSender;
    currentPartitionId = currentPartition;
  }

  /**
   * Starts a new command distribution request.
   *
   * @param distributionKey the key to identify this unique command distribution. The key is used to
   *     store the pending distribution, as the key of distributed command, to identify the
   *     distributing partition when processing the distributed command, and as the key to correlate
   *     the ACKNOWLEDGE command to the pending distribution. This can be a newly generated key, or
   *     the key identifying the entity that's being distributed. Please note that it must be unique
   *     for command distribution. Don't reuse the key to distribute another command. Don't reuse
   *     the key to distribute another command until the previous command distribution has been
   *     completed. Additionally, the key determines the order in which the commands are distributed
   *     if they are distributed ordered, i.e. a queue is specified.
   */
  public CommandDistributionRequestBuilder withKey(final long distributionKey) {
    return new DistributionRequest(distributionKey);
  }

  /**
   * Distributes a command to the specified partitions.
   *
   * @param distributionKey the key to identify this unique command distribution. The key is used to
   *     store the pending distribution, as the key of distributed command, to identify the
   *     distributing partition when processing the distributed command, and as the key to correlate
   *     the ACKNOWLEDGE command to the pending distribution. This can be a newly generated key, or
   *     the key identifying the entity that's being distributed. Please note that it must be unique
   *     for command distribution. Don't reuse the key to distribute another command. Don't reuse
   *     the key to distribute another command until the previous command distribution has been
   *     completed.
   * @param queue the queue to distribute the command to or null if the distribution can be
   *     unordered.
   * @param valueType the type of the command to distribute
   * @param intent the intent of the command to distribute
   * @param value the value of the command to distribute
   * @param partitions the partitions to distribute the command to
   */
  private <T extends UnifiedRecordValue> void distributeCommand(
      final String queue,
      final long distributionKey,
      final ValueType valueType,
      final Intent intent,
      final T value,
      final Set<Integer> partitions) {
    if (partitions.isEmpty()
        || (partitions.size() == 1 && partitions.contains(currentPartitionId))) {
      return;
    }

    commandDistributionStarted.reset();

    final var distributionRecord =
        commandDistributionStarted
            .setQueueId(queue)
            .setPartitionId(currentPartitionId)
            .setValueType(valueType)
            .setIntent(intent)
            .setCommandValue(value);

    stateWriter.appendFollowUpEvent(
        distributionKey, CommandDistributionIntent.STARTED, distributionRecord);

    partitions.forEach(
        (partition) -> {
          if (partition == currentPartitionId) {
            return;
          }
          distributeToPartition(partition, distributionRecord, distributionKey);
        });
  }

  private void distributeToPartition(
      final int partition,
      final CommandDistributionRecord distributionRecord,
      final long distributionKey) {
    final var distributionQueue = Optional.ofNullable(distributionRecord.getQueueId());
    distributionQueue.ifPresent(queue -> enqueueDistribution(queue, partition, distributionKey));

    final var canDistributeImmediately =
        distributionQueue
            .flatMap(queue -> distributionState.getNextQueuedDistributionKey(queue, partition))
            .filter(nextDistributionKey -> nextDistributionKey != distributionKey)
            .isEmpty();

    // Only distribute immediately if there are no other distributions in the queue.
    // If there are, we skip distributing immediately and wait the preceding distribution to be
    // acknowledged which then triggers this distribution.
    if (canDistributeImmediately) {
      startDistributing(partition, distributionRecord, distributionKey);
    }
  }

  private void enqueueDistribution(
      final String queue, final int partition, final long distributionKey) {
    commandDistributionEnqueued.reset();
    stateWriter.appendFollowUpEvent(
        distributionKey,
        CommandDistributionIntent.ENQUEUED,
        commandDistributionEnqueued.setQueueId(queue).setPartitionId(partition));
  }

  /**
   * If the given distribution was part of a queue, the next distribution from the queue is started.
   */
  void distributeNextInQueue(final long finishedDistributionKey, final int partition) {
    distributionState
        .getQueueIdForDistribution(finishedDistributionKey)
        .flatMap(queue -> distributionState.getNextQueuedDistributionKey(queue, partition))
        .ifPresent(
            nextDistributionKey ->
                startDistributing(
                    partition,
                    distributionState.getCommandDistributionRecord(nextDistributionKey, partition),
                    nextDistributionKey));
  }

  private void startDistributing(
      final int partition,
      final CommandDistributionRecord distributionRecord,
      final long distributionKey) {
    final var valueType = distributionRecord.getValueType();
    final var intent = distributionRecord.getIntent();

    commandDistributionDistributing.reset();

    // We don't need the actual record in the DISTRIBUTING event applier. In order to prevent
    // reaching the max message size we don't set the record value here.
    stateWriter.appendFollowUpEvent(
        distributionKey,
        CommandDistributionIntent.DISTRIBUTING,
        commandDistributionDistributing
            .setPartitionId(partition)
            .setValueType(valueType)
            .setIntent(intent));

    // This getter makes a hard copy of the command value, which we need to send the command to the
    // other partition in a side effect. It does not appear to be possible to reuse a single
    // instance for distributing to all partitions in the form of a method parameter, but it's not
    // fully clear why that leads to problems. We suspect that it's because the command value is a
    // mutable object, and it is somehow modified before the side effect is executed. Instead, we
    // have to copy this value for every partition.
    final var commandValue = distributionRecord.getCommandValue();

    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              partition, valueType, intent, distributionKey, commandValue);
          return true;
        });
  }

  /**
   * Acknowledges that a command was distributed to another partition successfully.
   *
   * @param command the command that was distributed
   */
  public <T extends UnifiedRecordValue> void acknowledgeCommand(final TypedRecord<T> command) {
    final long distributionKey = command.getKey();

    commandDistributionAcknowledge.reset();

    final var distributionRecord =
        commandDistributionAcknowledge
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setIntent(command.getIntent());

    final int receiverPartitionId = Protocol.decodePartitionId(distributionKey);
    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              receiverPartitionId,
              ValueType.COMMAND_DISTRIBUTION,
              CommandDistributionIntent.ACKNOWLEDGE,
              distributionKey,
              distributionRecord);
          return true;
        });
  }

  public interface CommandDistributionRequestBuilder {
    /**
     * Distributes the command in an unordered way. This means that the command is sent to the
     * receiving partitions without any guarantee of order.
     *
     * @return the builder
     */
    CommandDistributionRequestBuilder unordered();

    /**
     * Appends this command to the specified queue to be distributed in the natural sort order of
     * the distribution key. Ordering is maintained separately for each partition the command is
     * distributed to.
     *
     * @param queue the queue to append the command to.
     */
    CommandDistributionRequestBuilder inQueue(String queue);

    /** Specifies the single partition that this command will be distributed to. */
    CommandDistributionRequestBuilder forPartition(int partition);

    /** Specifies the partitions that this command will be distributed to. */
    CommandDistributionRequestBuilder forPartitions(Set<Integer> partitions);

    /** Specifies that this command will be distributed to all partitions except the local one. */
    CommandDistributionRequestBuilder forOtherPartitions();

    /** Distributes the command as specified. */
    <T extends UnifiedRecordValue> void distribute(TypedRecord<T> command);

    /** Distributes the command as specified. */
    <T extends UnifiedRecordValue> void distribute(
        final ValueType valueType, final Intent intent, final T value);
  }

  private class DistributionRequest implements CommandDistributionRequestBuilder {
    final long distributionKey;
    String queue;
    Set<Integer> partitions = routingInfo.partitions();

    public DistributionRequest(final long distributionKey) {
      this.distributionKey = distributionKey;
    }

    @Override
    public CommandDistributionRequestBuilder unordered() {
      queue = null;
      return this;
    }

    @Override
    public CommandDistributionRequestBuilder inQueue(final String queue) {
      this.queue = Objects.requireNonNull(queue);
      return this;
    }

    @Override
    public CommandDistributionRequestBuilder forPartition(final int partition) {
      partitions = Set.of(partition);
      return this;
    }

    @Override
    public CommandDistributionRequestBuilder forPartitions(final Set<Integer> partitions) {
      this.partitions = Objects.requireNonNull(partitions);
      return this;
    }

    @Override
    public CommandDistributionRequestBuilder forOtherPartitions() {
      partitions = routingInfo.partitions();
      return this;
    }

    @Override
    public <T extends UnifiedRecordValue> void distribute(final TypedRecord<T> command) {
      distributeCommand(
          queue,
          distributionKey,
          command.getValueType(),
          command.getIntent(),
          command.getValue(),
          partitions);
    }

    @Override
    public <T extends UnifiedRecordValue> void distribute(
        final ValueType valueType, final Intent intent, final T value) {
      distributeCommand(
          queue,
          distributionKey,
          Objects.requireNonNull(valueType),
          Objects.requireNonNull(intent),
          Objects.requireNonNull(value),
          partitions);
    }
  }
}

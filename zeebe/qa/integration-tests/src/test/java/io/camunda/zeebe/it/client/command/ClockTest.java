/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.it.util.ZeebeAssertHelper.assertClockPinned;
import static io.camunda.zeebe.it.util.ZeebeAssertHelper.assertClockResetted;
import static io.camunda.zeebe.it.util.ZeebeAssertHelper.assertElementRecordInState;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
@AutoCloseResources
class ClockTest {

  private static final long FIXED_TIME = 4366239393222L; // Sat May 12 2108 04:16:33 GMT+0000
  private static final Instant FIXED_INSTANT = Instant.ofEpochMilli(FIXED_TIME);
  private static final Duration DELTA = Duration.ofSeconds(1);

  @AutoCloseResource private ZeebeClient client;
  private ZeebeResourcesHelper resourcesHelper;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldPinClockToTimestamp() {
    // when
    client.newClockPinCommand().time(FIXED_TIME).send().join();

    // then
    assertClockPinned(c -> assertThat(c).hasTime(FIXED_TIME));
  }

  @Test
  void shouldPinClockToInstant() {
    // when
    client.newClockPinCommand().time(FIXED_INSTANT).send().join();

    // then
    assertClockPinned(c -> assertThat(c).hasTime(FIXED_TIME));
  }

  @Test
  void shouldRejectPinOperationIfNoTimestampProvided() {
    // when / then
    assertThatThrownBy(() -> client.newClockPinCommand().send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .extracting(e -> (ProblemException) e.getCause())
        .satisfies(
            e -> {
              assertThat(e.getMessage()).startsWith("Failed with code 400: 'Bad Request'");
              assertThat(e.details().getTitle()).isEqualTo("INVALID_ARGUMENT");
              assertThat(e.details().getDetail()).isEqualTo("No timestamp provided.");
            });
  }

  @Test
  void shouldResetClock() {
    // when
    client.newClockResetCommand().send().join();

    // then
    assertClockResetted(c -> assertThat(c).hasTime(0L));
  }

  static Stream<Instant> validInstances() {
    return Stream.of(
        Instant.EPOCH,
        Instant.now().truncatedTo(ChronoUnit.MILLIS),
        Instant.ofEpochMilli(Long.MAX_VALUE));
  }

  @ParameterizedTest
  @MethodSource("validInstances")
  void shouldCompleteProcessAtPinnedTime(final Instant validInstant) {
    // given
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("simple_process").startEvent().endEvent().done());

    client.newClockPinCommand().time(validInstant).send().join();

    // when: create a process instance while clock is pinned
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then
    assertElementRecordInState(
        processInstanceKey,
        "simple_process",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        r ->
            assertThatTimestampIsEqualToInstant(
                r.getTimestamp(), validInstant, "Process should complete at the pinned time"));
  }

  @Test
  void shouldCompleteProcessesAtPinnedTimeAndCurrentTimeAfterReset() {
    // given
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("simple_process").startEvent().endEvent().done());

    // and: pin the clock to a fixed time
    client.newClockPinCommand().time(FIXED_INSTANT).send().join();

    // when: create a process instance while clock is pinned
    final long firstProcessInstanceKey =
        resourcesHelper.createProcessInstance(processDefinitionKey);

    // then
    assertElementRecordInState(
        firstProcessInstanceKey,
        "simple_process",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        r -> {
          final Instant processCompletedInstant = Instant.ofEpochMilli(r.getTimestamp());
          assertThat(processCompletedInstant)
              .as("Process should complete at the pinned time")
              .isEqualTo(FIXED_INSTANT);
        });

    // when: reset the clock back to the current system time
    client.newClockResetCommand().send().join();

    // and: create a new process instance after the clock reset
    final long secondProcessInstanceKey =
        resourcesHelper.createProcessInstance(processDefinitionKey);

    // then
    assertElementRecordInState(
        secondProcessInstanceKey,
        "simple_process",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        r ->
            assertTimestampCloseToNow(
                r.getTimestamp(),
                "Process should complete near the current time after clock reset"));
  }

  @Test
  void shouldTriggerIntermediateTimerEventWhenClockPinnedToFutureTime() {
    // given: deploy a process with intermediate timer event set to date in future
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process_with_timer_event_in_future")
                .startEvent()
                .intermediateCatchEvent("timerEvent")
                .timerWithDate(FIXED_INSTANT.toString())
                .endEvent()
                .done());

    // when
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then
    assertElementRecordInState(
        processInstanceKey,
        "timerEvent",
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        r ->
            assertTimestampCloseToNow(
                r.getTimestamp(), "Timer event should be activated near the current time"));

    // when: pin the clock to the timer date
    client.newClockPinCommand().time(FIXED_INSTANT).send().join();

    // then
    assertElementRecordInState(
        processInstanceKey,
        "timerEvent",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        r ->
            assertThatTimestampIsEqualToInstant(
                r.getTimestamp(), FIXED_INSTANT, "Timer event should complete at the pinned time"));
  }

  @Test
  void shouldTriggerTimerStartEventWhenClockPinnedToFutureTime() {
    // when: deploy a process with timer start event set to date in future
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process_with_timer_start_event_in_future")
                .startEvent("timerStartEvent")
                .timerWithDate(FIXED_INSTANT.toString())
                .endEvent()
                .done());

    // when: pin the clock to the timer date
    client.newClockPinCommand().time(FIXED_INSTANT).send().join();

    // then
    final long processInstanceKey = getProcessInstanceKey(processDefinitionKey);
    assertElementRecordInState(
        processInstanceKey,
        "timerStartEvent",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        r ->
            assertThatTimestampIsEqualToInstant(
                r.getTimestamp(),
                FIXED_INSTANT,
                "Timer start event should complete at the pinned time"));
  }

  @Test
  void shouldTriggerBoundaryTimerEventWhenClockPinnedToFutureTime() {
    // given: deploy a process with boundary timer event set to date in future
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process_with_boundary_timer_event")
                .startEvent()
                .serviceTask("boundary_event_owner", t -> t.zeebeJobType("service_task"))
                .boundaryEvent(
                    "boundary_timer_event", t -> t.timerWithDate(FIXED_INSTANT.toString()))
                .endEvent("boundary_end")
                .moveToActivity("boundary_event_owner")
                .endEvent("main_end")
                .done());

    // when
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then
    assertElementRecordInState(
        processInstanceKey,
        "boundary_event_owner",
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        r ->
            assertTimestampCloseToNow(
                r.getTimestamp(), "Service task should be activated near the current time"));

    // when: pin the clock to the timer date
    final Instant futureInstant = FIXED_INSTANT.plus(Duration.ofHours(1));
    client.newClockPinCommand().time(futureInstant).send().join();

    // then
    assertElementRecordInState(
        processInstanceKey,
        "boundary_timer_event",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        r ->
            assertThatTimestampIsEqualToInstant(
                r.getTimestamp(),
                futureInstant,
                "Boundary timer event should complete at the pinned time"));
  }

  private void assertThatTimestampIsEqualToInstant(
      final long actualTimestamp, final Instant expectedInstant, final String description) {
    final Instant processCompletedInstant = Instant.ofEpochMilli(actualTimestamp);
    assertThat(processCompletedInstant).as(description).isEqualTo(expectedInstant);
  }

  private static void assertTimestampCloseToNow(final long actual, final String description) {
    final Instant now = Instant.now();
    assertThat(Instant.ofEpochMilli(actual)).as(description).isBetween(now.minus(DELTA), now);
  }

  private static long getProcessInstanceKey(final long processDefinitionKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getKey();
  }
}

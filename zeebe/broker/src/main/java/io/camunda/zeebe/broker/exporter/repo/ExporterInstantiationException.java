/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

public final class ExporterInstantiationException extends RuntimeException {
  private static final long serialVersionUID = -7231999951981994615L;
  private static final String MESSAGE_FORMAT = "Cannot instantiate exporter [%s]";

  public ExporterInstantiationException(final String id, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, id), cause);
  }
}

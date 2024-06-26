/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class NextValue extends UnpackedObject implements DbValue {
  private final LongProperty nextValueProp = new LongProperty("nextValue", -1L);

  public NextValue() {
    super(1);
    declareProperty(nextValueProp);
  }

  public void set(final long value) {
    nextValueProp.setValue(value);
  }

  public long get() {
    return nextValueProp.getValue();
  }
}

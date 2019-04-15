/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

public class RelativeDateFilterStartDto {

  protected Long value;
  protected RelativeDateFilterUnit unit;

  public RelativeDateFilterStartDto() {
  }

  public RelativeDateFilterStartDto(Long value, RelativeDateFilterUnit unit) {
    this.unit = unit;
    this.value = value;
  }

  public Long getValue() {
    return value;
  }

  public void setValue(Long value) {
    this.value = value;
  }

  public RelativeDateFilterUnit getUnit() {
    return unit;
  }

  public void setUnit(RelativeDateFilterUnit unit) {
    this.unit = unit;
  }

}

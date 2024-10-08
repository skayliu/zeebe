/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities.listview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.entities.VariableEntity;

public class VariableListViewEntity {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String value;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String fullValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isPreview;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String scopeKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer partitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String tenantId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ListViewJoinRelation join;

  public VariableListViewEntity(final VariableEntity entity) {
    setValue(entity.getValue());
    setFullValue(entity.getFullValue());
    setName(entity.getName());
    setIsPreview(entity.getIsPreview());
    setScopeKey(entity.getScopeFlowNodeId());
    setId(entity.getId());
    setPartitionId(entity.getPartitionId());
    setTenantId(entity.getTenantId());

    // Set the join relation
    final ListViewJoinRelation joinRelation = new ListViewJoinRelation();
    setJoin(joinRelation);
  }

  // Add getter and setters
  public String getId() {
    return id;
  }

  public VariableListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableListViewEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableListViewEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableListViewEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public Boolean getIsPreview() {
    return isPreview;
  }

  public VariableListViewEntity setIsPreview(final Boolean isPreview) {
    this.isPreview = isPreview;
    return this;
  }

  public String getScopeKey() {
    return scopeKey;
  }

  public VariableListViewEntity setScopeKey(final String scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public VariableListViewEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ListViewJoinRelation getJoin() {
    return join;
  }

  public VariableListViewEntity setJoin(final ListViewJoinRelation join) {
    this.join = join;
    return this;
  }
}

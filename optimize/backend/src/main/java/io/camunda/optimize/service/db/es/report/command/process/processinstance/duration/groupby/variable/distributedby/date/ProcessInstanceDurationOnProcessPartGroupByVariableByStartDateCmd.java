/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.process.processinstance.duration.groupby.variable.distributedby.date;

import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import io.camunda.optimize.service.db.es.report.command.ProcessCmd;
import io.camunda.optimize.service.db.es.report.command.exec.ProcessReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.ProcessDistributedByInstanceStartDate;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.ProcessGroupByVariable;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.duration.ProcessViewInstanceDurationOnProcessPart;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceDurationOnProcessPartGroupByVariableByStartDateCmd
    extends ProcessCmd<List<HyperMapResultEntryDto>> {

  public ProcessInstanceDurationOnProcessPartGroupByVariableByStartDateCmd(
      final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<HyperMapResultEntryDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .processCommand()
        .view(ProcessViewInstanceDurationOnProcessPart.class)
        .groupBy(ProcessGroupByVariable.class)
        .distributedBy(ProcessDistributedByInstanceStartDate.class)
        .resultAsHyperMap()
        .build();
  }
}

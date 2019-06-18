/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.aggregations.AvgAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MaxAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MedianAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MinAggregation;
import org.camunda.optimize.service.es.report.command.decision.RawDecisionDataCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByEvaluationDateTimeCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByInputVariableCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByMatchedRuleCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByOutputVariableCommand;
import org.camunda.optimize.service.es.report.command.process.RawProcessDataCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.duration.FlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.frequency.CountFlowNodeFrequencyByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.ProcessInstanceDurationGroupByEndDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.ProcessInstanceDurationGroupByEndDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.ProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.ProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.ProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.ProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.ProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.ProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByEndDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.assignee.UserTaskIdleDurationByAssigneeCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.assignee.UserTaskTotalDurationByAssigneeCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.assignee.UserTaskWorkDurationByAssigneeCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.usertask.UserTaskIdleDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.usertask.UserTaskTotalDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.usertask.UserTaskWorkDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByEvaluationDateTimeReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByInputVariableReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByMatchedRuleReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByOutputVariableReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createRawDecisionDataReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountFlowNodeFrequencyGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByEndDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByEndDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByEndDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createRawDataReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskIdleDurationGroupByAssigneeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskIdleDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskTotalDurationGroupByAssigneeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskTotalDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskWorkDurationGroupByAssigneeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskWorkDurationGroupByUserTaskReport;

@RequiredArgsConstructor
@Component
public class SingleReportEvaluator {

  private static Map<String, Supplier<? extends Command>> commandSuppliers = new HashMap<>();

  static {
    // process reports
    commandSuppliers.put(createRawDataReport().createCommandKey(), RawProcessDataCommand::new);

    addCountProcessInstanceFrequencyReports();
    addCountFlowNodeFrequencyReports();

    addProcessInstanceDurationReports();
    addFlowNodeDurationReports();
    addUserTaskDurationReports();

    // decision reports
    commandSuppliers.put(createRawDecisionDataReport().createCommandKey(), RawDecisionDataCommand::new);

    addDecisionCountFrequencyReports();
  }

  protected final ConfigurationService configurationService;
  protected final ObjectMapper objectMapper;
  protected final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  protected final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  protected final RestHighLevelClient esClient;
  protected final IntervalAggregationService intervalAggregationService;
  protected final ProcessDefinitionReader processDefinitionReader;

  private static void addCountProcessInstanceFrequencyReports() {
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByNoneReport().createCommandKey(),
      CountProcessInstanceFrequencyGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByStartDateReport().createCommandKey(),
      CountProcessInstanceFrequencyByStartDateCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByEndDateReport().createCommandKey(),
      CountProcessInstanceFrequencyByEndDateCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByVariableReport().createCommandKey(),
      CountProcessInstanceFrequencyByVariableCommand::new
    );
  }

  private static void addCountFlowNodeFrequencyReports() {
    commandSuppliers.put(
      createCountFlowNodeFrequencyGroupByFlowNodeReport().createCommandKey(),
      CountFlowNodeFrequencyByFlowNodeCommand::new
    );
  }

  private static void addFlowNodeDurationReports() {
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createFlowNodeDurationGroupByFlowNodeReport(aggro).createCommandKey(),
      FlowNodeDurationByFlowNodeCommand::new
    );
  }

  private static void createCommandsForAllAggregationTypes(Function<AggregationType, String> createCommandKeyFunction,
                                                           Function<AggregationStrategy, Command> createCommandFunction) {
    for (AggregationType currAggro : AggregationType.values()) {
      createCommandForAggregationType(currAggro, createCommandKeyFunction, createCommandFunction);
    }
  }


  private static void createCommandForAggregationType(AggregationType aggregationType,
                                                      Function<AggregationType, String> createCommandKeyFunction,
                                                      Function<AggregationStrategy, Command> createCommandFunction) {
    switch (aggregationType) {
      case MIN:
        commandSuppliers.put(
          createCommandKeyFunction.apply(aggregationType), () -> createCommandFunction.apply(new MinAggregation())
        );
        break;
      case MAX:
        commandSuppliers.put(
          createCommandKeyFunction.apply(aggregationType), () -> createCommandFunction.apply(new MaxAggregation())
        );
        break;
      case AVERAGE:
        commandSuppliers.put(
          createCommandKeyFunction.apply(aggregationType), () -> createCommandFunction.apply(new AvgAggregation())
        );
        break;
      case MEDIAN:
        commandSuppliers.put(
          createCommandKeyFunction.apply(aggregationType), () -> createCommandFunction.apply(new MedianAggregation())
        );
        break;
      default:
        throw new OptimizeRuntimeException(String.format("Unknown aggregation type [%s]", aggregationType));
    }

  }

  private static void addUserTaskDurationReports() {
    createUserTaskDurationGroupedByAssigneeReports();
    createUserTaskDurationGroupedByUserTaskReports();
  }

  private static void createUserTaskDurationGroupedByUserTaskReports() {
    // IDLE USER TASKS
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createUserTaskIdleDurationGroupByUserTaskReport(aggro).createCommandKey(),
      UserTaskIdleDurationByUserTaskCommand::new
    );

    // TOTAL USER TASKS
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createUserTaskTotalDurationGroupByUserTaskReport(aggro).createCommandKey(),
      UserTaskTotalDurationByUserTaskCommand::new
    );

    // WORK USER TASKS
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createUserTaskWorkDurationGroupByUserTaskReport(aggro).createCommandKey(),
      UserTaskWorkDurationByUserTaskCommand::new
    );
  }

  private static void createUserTaskDurationGroupedByAssigneeReports() {
    // IDLE USER TASKS
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createUserTaskIdleDurationGroupByAssigneeReport(aggro).createCommandKey(),
      UserTaskIdleDurationByAssigneeCommand::new
    );

    // TOTAL USER TASKS
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createUserTaskTotalDurationGroupByAssigneeReport(aggro).createCommandKey(),
      UserTaskTotalDurationByAssigneeCommand::new
    );

    // WORK USER TASKS
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createUserTaskWorkDurationGroupByAssigneeReport(aggro).createCommandKey(),
      UserTaskWorkDurationByAssigneeCommand::new
    );
  }

  private static void addProcessInstanceDurationReports() {
    // GROUP BY NONE
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByNoneReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByNoneCommand::new
    );
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByNoneWithProcessPartReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByNoneWithProcessPartCommand::new
    );

    // GROUP BY START DATE
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByStartDateReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByStartDateCommand::new
    );
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByStartDateWithProcessPartReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByStartDateWithProcessPartCommand::new
    );

    // GROUP BY END DATE
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByEndDateReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByEndDateCommand::new
    );
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByEndDateWithProcessPartReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByEndDateWithProcessPartCommand::new
    );

    // GROUP BY VARIABLE
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByVariableReport(aggro).createCommandKey(),
      ProcessInstanceDurationByVariableCommand::new
    );
    createCommandsForAllAggregationTypes(
      (AggregationType aggro) -> createProcessInstanceDurationGroupByVariableWithProcessPartReport(aggro).createCommandKey(),
      ProcessInstanceDurationGroupByVariableWithProcessPartCommand::new
    );
  }

  private static void addDecisionCountFrequencyReports() {
    commandSuppliers.put(
      createCountFrequencyGroupByNoneReport().createCommandKey(),
      CountDecisionFrequencyGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByEvaluationDateTimeReport().createCommandKey(),
      CountDecisionFrequencyGroupByEvaluationDateTimeCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByInputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByInputVariableCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByOutputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByOutputVariableCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByMatchedRuleReport().createCommandKey(),
      CountDecisionFrequencyGroupByMatchedRuleCommand::new
    );
  }

  <T extends ReportDefinitionDto> ReportEvaluationResult<?, T> evaluate(T reportDefinition) throws OptimizeException {
    CommandContext<T> commandContext = createCommandContext(reportDefinition);
    Command<T> evaluationCommand = extractCommandWithValidation(reportDefinition);
    return evaluationCommand.evaluate(commandContext);
  }

  protected <T extends ReportDefinitionDto> CommandContext<T> createCommandContext(T reportDefinition) {
    CommandContext<T> commandContext = new CommandContext<>();
    commandContext.setConfigurationService(configurationService);
    commandContext.setEsClient(esClient);
    commandContext.setObjectMapper(objectMapper);
    commandContext.setIntervalAggregationService(intervalAggregationService);
    if (reportDefinition instanceof SingleProcessReportDefinitionDto) {
      commandContext.setQueryFilterEnhancer(processQueryFilterEnhancer);
    } else if (reportDefinition instanceof SingleDecisionReportDefinitionDto) {
      commandContext.setQueryFilterEnhancer(decisionQueryFilterEnhancer);
    }
    commandContext.setProcessDefinitionReader(processDefinitionReader);
    commandContext.setReportDefinition(reportDefinition);
    return commandContext;
  }

  private <T extends ReportDefinitionDto> Command<T> extractCommandWithValidation(T reportDefinition) {
    ValidationHelper.validate(reportDefinition.getData());
    return extractCommand(reportDefinition);
  }

  @SuppressWarnings(value = "unchecked")
  <T extends ReportDefinitionDto> Command<T> extractCommand(T reportDefinition) {
    return commandSuppliers.getOrDefault(reportDefinition.getData().createCommandKey(), NotSupportedCommand::new).get();
  }
}

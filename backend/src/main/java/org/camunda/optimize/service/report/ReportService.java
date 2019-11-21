/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.report;

import com.google.common.collect.ImmutableSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeReportConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeScopeComplianceException;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.extractProcessDefinitionName;
import static org.camunda.optimize.service.engine.importing.DmnModelUtility.extractDecisionDefinitionName;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportService implements CollectionReferencingService {
  private static final String DEFAULT_REPORT_NAME = "New Report";
  private static final String REPORT_NOT_IN_SAME_COLLECTION_ERROR_MESSAGE = "Either the report %s does not reside in " +
    "the same collection as the combined report or both are not private entities";

  private final ReportWriter reportWriter;
  private final ReportReader reportReader;
  private final AuthorizationCheckReportEvaluationHandler reportEvaluator;
  private final ReportAuthorizationService reportAuthorizationService;
  private final ReportRelationService reportRelationService;

  private final AuthorizedCollectionService collectionService;

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final SimpleCollectionDefinitionDto definition) {
    return reportReader.findReportsForCollectionOmitXml(definition.getId()).stream()
      .map(reportDefinitionDto -> new ConflictedItemDto(
        reportDefinitionDto.getId(), ConflictedItemType.COLLECTION, reportDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final SimpleCollectionDefinitionDto definition) {
    reportWriter.deleteAllReportsOfCollection(definition.getId());
  }

  public IdDto createNewSingleDecisionReport(final String userId,
                                             final SingleDecisionReportDefinitionDto definitionDto) {
    compliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    return createReport(
      userId, definitionDto, DecisionReportDataDto::new, reportWriter::createNewSingleDecisionReport
    );
  }

  public IdDto createNewSingleProcessReport(final String userId,
                                            final SingleProcessReportDefinitionDto definitionDto) {
    compliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    return createReport(
      userId, definitionDto, ProcessReportDataDto::new, reportWriter::createNewSingleProcessReport
    );
  }

  public IdDto createNewCombinedProcessReport(final String userId,
                                              final CombinedReportDefinitionDto combinedReportDefinitionDto) {
    verifyValidReportCombination(
      userId,
      combinedReportDefinitionDto.getCollectionId(),
      combinedReportDefinitionDto.getData()
    );
    return createReport(
      userId, combinedReportDefinitionDto, CombinedReportDataDto::new, reportWriter::createNewCombinedReport
    );
  }

  public ConflictResponseDto getReportDeleteConflictingItems(String userId, String reportId) {
    ReportDefinitionDto currentReportVersion = getReportDefinition(reportId, userId).getDefinitionDto();
    return new ConflictResponseDto(getConflictedItemsForDeleteReport(currentReportVersion));
  }

  public IdDto copyReport(final String reportId, final String userId, final String newReportName) {
    final AuthorizedReportDefinitionDto authorizedReportDefinition = getReportDefinition(reportId, userId);
    final ReportDefinitionDto oldReportDefinition = authorizedReportDefinition.getDefinitionDto();

    return copyAndMoveReport(reportId, userId, oldReportDefinition.getCollectionId(), newReportName, new HashMap<>());
  }

  public IdDto copyAndMoveReport(final String reportId,
                                 final String userId,
                                 final String collectionId,
                                 final String newReportName) {
    return copyAndMoveReport(reportId, userId, collectionId, newReportName, new HashMap<>());
  }

  private IdDto copyAndMoveReport(final String reportId,
                                  final String userId,
                                  final String collectionId,
                                  final String newReportName,
                                  final Map<String, String> existingReportCopies) {
    return copyAndMoveReport(reportId, userId, collectionId, newReportName, existingReportCopies, false);
  }

  public IdDto copyAndMoveReport(final String reportId,
                                 final String userId,
                                 final String collectionId,
                                 final String newReportName,
                                 final Map<String, String> existingReportCopies,
                                 final boolean keepReportNames) {
    final AuthorizedReportDefinitionDto authorizedReportDefinition = getReportDefinition(reportId, userId);
    final ReportDefinitionDto originalReportDefinition = authorizedReportDefinition.getDefinitionDto();
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    final String oldCollectionId = originalReportDefinition.getCollectionId();
    final String newCollectionId = Objects.equals(oldCollectionId, collectionId) ? oldCollectionId : collectionId;

    return copyAndMoveReport(
      originalReportDefinition,
      userId,
      newReportName,
      newCollectionId,
      existingReportCopies,
      keepReportNames
    );
  }

  public AuthorizedReportDefinitionDto getReportDefinition(final String reportId, final String userId) {
    final ReportDefinitionDto report = reportReader.getReport(reportId);
    final RoleType currentUserRole = reportAuthorizationService.getAuthorizedRole(userId, report)
      .orElseThrow(() -> new ForbiddenException(String.format(
        "User [%s] is not authorized to access report [%s].", userId, reportId
      )));
    return new AuthorizedReportDefinitionDto(report, currentUserRole);
  }

  public List<AuthorizedReportDefinitionDto> findAndFilterPrivateReports(String userId,
                                                                         MultivaluedMap<String, String> queryParameters) {
    List<ReportDefinitionDto> reports = reportReader.getAllPrivateReportsOmitXml();
    List<AuthorizedReportDefinitionDto> authorizedReports = filterAuthorizedReports(userId, reports);
    return QueryParamAdjustmentUtil.adjustReportResultsToQueryParameters(authorizedReports, queryParameters);
  }

  public List<AuthorizedReportDefinitionDto> findAndFilterReports(String userId) {
    List<ReportDefinitionDto> reports = reportReader.getAllReportsOmitXml();
    return filterAuthorizedReports(userId, reports);
  }

  public List<AuthorizedReportDefinitionDto> findAndFilterReports(String userId, String collectionId) {
    // verify user is authorized to access collection
    collectionService.getAuthorizedSimpleCollectionDefinitionOrFail(userId, collectionId);

    List<ReportDefinitionDto> reportsInCollection = reportReader.findReportsForCollectionOmitXml(collectionId);
    return filterAuthorizedReports(userId, reportsInCollection);
  }

  public AuthorizedReportEvaluationResult evaluateSavedReport(String userId, String reportId) {
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateSavedReport(userId, reportId);
  }

  public AuthorizedReportEvaluationResult evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateReport(userId, reportDefinition);
  }

  public void updateCombinedProcessReport(String userId,
                                          String combinedReportId,
                                          CombinedReportDefinitionDto updatedReport) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());

    final ReportDefinitionDto currentReportVersion = getReportDefinition(combinedReportId, userId).getDefinitionDto();
    final AuthorizedReportDefinitionDto authorizedCombinedReport =
      getReportWithEditAuthorization(userId, currentReportVersion);
    final String combinedReportCollectionId = authorizedCombinedReport.getDefinitionDto().getCollectionId();

    final CombinedProcessReportDefinitionUpdateDto reportUpdate =
      convertToCombinedProcessReportUpdate(updatedReport, userId);

    final CombinedReportDataDto data = reportUpdate.getData();
    verifyValidReportCombination(userId, combinedReportCollectionId, data);
    reportWriter.updateCombinedReport(reportUpdate);
  }

  private void verifyValidReportCombination(final String userId, final String combinedReportCollectionId,
                                            final CombinedReportDataDto data) {
    if (data.getReportIds() != null && !data.getReportIds().isEmpty()) {
      final List<SingleProcessReportDefinitionDto> reportsOfCombinedReport = reportReader
        .getAllSingleProcessReportsForIdsOmitXml(data.getReportIds());

      final SingleProcessReportDefinitionDto firstReport = reportsOfCombinedReport.get(0);
      final boolean allReportsCanBeCombined = reportsOfCombinedReport.stream()
        .peek(report -> {
          final ReportDefinitionDto reportDefinition = getReportDefinition(report.getId(), userId).getDefinitionDto();

          if (!Objects.equals(combinedReportCollectionId, reportDefinition.getCollectionId())) {
            throw new BadRequestException(String.format(
              REPORT_NOT_IN_SAME_COLLECTION_ERROR_MESSAGE, reportDefinition.getId()
            ));
          }
        })
        .noneMatch(report -> semanticsForCombinedReportChanged(firstReport, report));
      if (allReportsCanBeCombined) {
        final ProcessVisualization visualization = firstReport.getData() == null
          ? null
          : firstReport.getData().getVisualization();
        data.setVisualization(visualization);
      } else {
        final String errorMessage =
          String.format(
            "Can't create or update combined report. " +
              "The following report ids are not combinable: [%s]",
            data.getReportIds()
          );
        log.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    }
  }

  public void updateSingleProcessReport(String reportId,
                                        SingleProcessReportDefinitionDto updatedReport,
                                        String userId,
                                        boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());

    final SingleProcessReportDefinitionDto currentReportVersion = getSingleProcessReportDefinition(
      reportId, userId
    );
    getReportWithEditAuthorization(userId, currentReportVersion);
    compliesWithCollectionScope(userId, currentReportVersion.getCollectionId(), updatedReport);

    final SingleProcessReportDefinitionUpdateDto reportUpdate = convertToSingleProcessReportUpdate(
      updatedReport, userId
    );

    if (!force) {
      checkForUpdateConflictsOnSingleProcessDefinition(currentReportVersion, updatedReport);
    }

    reportWriter.updateSingleProcessReport(reportUpdate);
    reportRelationService.handleUpdated(reportId, updatedReport);

    if (semanticsForCombinedReportChanged(currentReportVersion, updatedReport)) {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
    }
  }

  public void updateSingleDecisionReport(String reportId,
                                         SingleDecisionReportDefinitionDto updatedReport,
                                         String userId,
                                         boolean force) throws OptimizeConflictException {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    final SingleDecisionReportDefinitionDto currentReportVersion =
      getSingleDecisionReportDefinition(reportId, userId);
    getReportWithEditAuthorization(userId, currentReportVersion);
    compliesWithCollectionScope(userId, currentReportVersion.getCollectionId(), updatedReport);

    final SingleDecisionReportDefinitionUpdateDto reportUpdate = convertToSingleDecisionReportUpdate(
      updatedReport, userId
    );

    if (!force) {
      checkForUpdateConflictsOnSingleDecisionDefinition(currentReportVersion, updatedReport);
    }

    reportWriter.updateSingleDecisionReport(reportUpdate);
    reportRelationService.handleUpdated(reportId, updatedReport);
  }

  public void deleteReport(String userId, String reportId, boolean force) {

    final ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    getReportWithEditAuthorization(userId, reportDefinition);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDeleteReport(reportDefinition);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeReportConflictException(conflictedItems);
      }
    }

    if (!reportDefinition.getCombined()) {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
      reportWriter.deleteSingleReport(reportId);
    } else {
      reportWriter.deleteCombinedReport(reportId);
    }

    reportRelationService.handleDeleted(reportDefinition);
  }

  private <T extends ReportDefinitionDto<RD>, RD extends ReportDataDto> IdDto createReport(
    final String userId,
    final T reportDefinition,
    final Supplier<RD> defaultDataProvider,
    final CreateReportMethod<RD> createReportMethod) {

    final Optional<T> optionalProvidedDefinition = Optional.ofNullable(reportDefinition);
    final String collectionId = optionalProvidedDefinition
      .map(ReportDefinitionDto::getCollectionId)
      .orElse(null);
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    return createReportMethod.create(
      userId,
      optionalProvidedDefinition.map(ReportDefinitionDto::getData).orElse(defaultDataProvider.get()),
      optionalProvidedDefinition.map(ReportDefinitionDto::getName).orElse(DEFAULT_REPORT_NAME),
      collectionId
    );
  }

  private AuthorizedReportDefinitionDto getReportWithEditAuthorization(final String userId,
                                                                       final ReportDefinitionDto reportDefinition) {
    final Optional<RoleType> authorizedRole = reportAuthorizationService.getAuthorizedRole(userId, reportDefinition);
    if (!authorizedRole.map(roleType -> roleType.ordinal() >= RoleType.EDITOR.ordinal()).orElse(false)) {
      throw new ForbiddenException(
        "User [" + userId + "] is not authorized to edit report [" + reportDefinition.getName() + "]."
      );
    }
    return new AuthorizedReportDefinitionDto(reportDefinition, authorizedRole.get());
  }

  private Set<ConflictedItemDto> mapCombinedReportsToConflictingItems(List<CombinedReportDefinitionDto> combinedReportDtos) {
    return combinedReportDtos.stream()
      .map(combinedReportDto -> new ConflictedItemDto(
        combinedReportDto.getId(), ConflictedItemType.COMBINED_REPORT, combinedReportDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  private IdDto copyAndMoveReport(final ReportDefinitionDto originalReportDefinition,
                                  final String userId,
                                  final String newReportName,
                                  final String newCollectionId,
                                  final Map<String, String> existingReportCopies,
                                  final boolean keepReportNames) {
    final String newName = newReportName != null ? newReportName : originalReportDefinition.getName() + " – Copy";
    final String oldCollectionId = originalReportDefinition.getCollectionId();

    if (!originalReportDefinition.getCombined()) {
      switch (originalReportDefinition.getReportType()) {
        case PROCESS:
          SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
            (SingleProcessReportDefinitionDto) originalReportDefinition;
          compliesWithCollectionScope(
            userId,
            newCollectionId,
            singleProcessReportDefinitionDto
          );
          return reportWriter.createNewSingleProcessReport(
            userId, singleProcessReportDefinitionDto.getData(), newName, newCollectionId
          );
        case DECISION:
          SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto =
            (SingleDecisionReportDefinitionDto) originalReportDefinition;
          compliesWithCollectionScope(
            userId,
            newCollectionId,
            singleDecisionReportDefinitionDto
          );
          return reportWriter.createNewSingleDecisionReport(
            userId, singleDecisionReportDefinitionDto.getData(), newName, newCollectionId
          );
        default:
          throw new IllegalStateException("Unsupported reportType: " + originalReportDefinition.getReportType());
      }
    } else {
      CombinedReportDefinitionDto combinedReportDefinition = (CombinedReportDefinitionDto) originalReportDefinition;
      return copyAndMoveCombinedReport(
        userId,
        newName,
        newCollectionId,
        oldCollectionId,
        combinedReportDefinition.getData(),
        existingReportCopies,
        keepReportNames
      );
    }
  }

  private IdDto copyAndMoveCombinedReport(final String userId,
                                          final String newName,
                                          final String newCollectionId,
                                          final String oldCollectionId,
                                          final CombinedReportDataDto oldCombinedReportData,
                                          final Map<String, String> existingReportCopies,
                                          final boolean keepReportNames) {
    final CombinedReportDataDto newCombinedReportData = new CombinedReportDataDto(
      oldCombinedReportData.getConfiguration(),
      oldCombinedReportData.getVisualization(),
      oldCombinedReportData.getReports()
    );

    if (!StringUtils.equals(newCollectionId, oldCollectionId)) {
      final List<CombinedReportItemDto> newReports = new ArrayList<>();
      oldCombinedReportData
        .getReports()
        .stream()
        .sequential()
        .peek(report ->  compliesWithCollectionScope(userId, newCollectionId, report.getId()))
        .forEach(combinedReportItemDto -> {
          String reportName = keepReportNames ? reportReader.getReport(combinedReportItemDto.getId()).getName() : null;
          final String reportCopyId = existingReportCopies.computeIfAbsent(
            combinedReportItemDto.getId(),
            reportId -> copyAndMoveReport(reportId, userId, newCollectionId, reportName).getId()
          );
          newReports.add(
            combinedReportItemDto.toBuilder().id(reportCopyId).color(combinedReportItemDto.getColor()).build()
          );
        });
      newCombinedReportData.setReports(newReports);
    }

    return reportWriter.createNewCombinedReport(userId, newCombinedReportData, newName, newCollectionId);
  }


  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    if (!reportDefinition.getCombined()) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportDefinition.getId()))
      );
    }
    conflictedItems.addAll(reportRelationService.getConflictedItemsForDeleteReport(reportDefinition));
    return conflictedItems;
  }

  public void compliesWithCollectionScope(final String userId, final String collectionId, final String reportId) {
    final ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    if (!reportDefinition.getCombined()) {
      SingleReportDefinitionDto singleProcessReportDefinitionDto =
            (SingleReportDefinitionDto) reportDefinition;
          compliesWithCollectionScope(userId, collectionId, singleProcessReportDefinitionDto);
      }
  }

  private void compliesWithCollectionScope(final String userId, final String collectionId,
                                           final SingleReportDefinitionDto<?> definition) {
    if (collectionId == null) {
      return;
    }

    final SimpleCollectionDefinitionDto collection =
      collectionService.getAuthorizedSimpleCollectionDefinitionOrFail(userId, collectionId).getDefinitionDto();

    final boolean definitionKeyDefined = definition.getData().getDefinitionKey() != null;
    boolean isAllowedForCollectionScope =
      collection.getData()
        .getScope()
        .stream()
        .anyMatch(scope -> {
          final SingleReportDataDto data = definition.getData();
          return scope.isInScope(definition.getDefinitionType(), data.getDefinitionKey(), data.getTenantIds());
        }) || !definitionKeyDefined;
    if (!isAllowedForCollectionScope) {
      final ConflictedItemDto conflictedItemDto = new ConflictedItemDto(
        collection.getId(),
        ConflictedItemType.COLLECTION,
        collection.getName()
      );
      throw new OptimizeScopeComplianceException(ImmutableSet.of(conflictedItemDto));
    }
  }

  private void checkForUpdateConflictsOnSingleProcessDefinition(SingleProcessReportDefinitionDto currentReportVersion,
                                                                SingleProcessReportDefinitionDto reportUpdateDto)
    throws OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();

    if (semanticsForCombinedReportChanged(currentReportVersion, reportUpdateDto)) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportId))
      );
    }

    conflictedItems.addAll(
      reportRelationService.getConflictedItemsForUpdatedReport(currentReportVersion, reportUpdateDto)
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private void checkForUpdateConflictsOnSingleDecisionDefinition(SingleDecisionReportDefinitionDto currentReportVersion,
                                                                 SingleDecisionReportDefinitionDto reportUpdateDto) throws
                                                                                                                    OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = reportRelationService.getConflictedItemsForUpdatedReport(
      currentReportVersion,
      reportUpdateDto
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private boolean semanticsForCombinedReportChanged(SingleProcessReportDefinitionDto firstReport,
                                                    SingleProcessReportDefinitionDto secondReport) {
    boolean result = false;
    if (firstReport.getData() != null) {
      ProcessReportDataDto oldData = firstReport.getData();
      SingleReportDataDto newData = secondReport.getData();
      result = !newData.isCombinable(oldData);
    }
    return result;
  }

  private SingleProcessReportDefinitionUpdateDto convertToSingleProcessReportUpdate(
    final SingleProcessReportDefinitionDto updatedReport,
    final String userId) {
    SingleProcessReportDefinitionUpdateDto reportUpdate = new SingleProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getProcessDefinitionKey();
      reportUpdate.getData().setProcessDefinitionName(
        extractProcessDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private SingleDecisionReportDefinitionUpdateDto convertToSingleDecisionReportUpdate(
    final SingleDecisionReportDefinitionDto updatedReport,
    final String userId) {

    SingleDecisionReportDefinitionUpdateDto reportUpdate = new SingleDecisionReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDecisionDefinitionKey();
      reportUpdate.getData().setDecisionDefinitionName(
        extractDecisionDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private CombinedProcessReportDefinitionUpdateDto convertToCombinedProcessReportUpdate(
    final CombinedReportDefinitionDto updatedReport,
    final String userId) {
    CombinedProcessReportDefinitionUpdateDto reportUpdate = new CombinedProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    return reportUpdate;
  }

  private SingleProcessReportDefinitionDto getSingleProcessReportDefinition(String reportId,
                                                                            String userId) {
    SingleProcessReportDefinitionDto report = reportReader.getSingleProcessReport(reportId);
    if (!reportAuthorizationService.getAuthorizedRole(userId, report).isPresent()) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private SingleDecisionReportDefinitionDto getSingleDecisionReportDefinition(String reportId,
                                                                              String userId) {
    SingleDecisionReportDefinitionDto report = reportReader.getSingleDecisionReport(reportId);
    if (!reportAuthorizationService.getAuthorizedRole(userId, report).isPresent()) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private List<AuthorizedReportDefinitionDto> filterAuthorizedReports(String userId,
                                                                      List<ReportDefinitionDto> reports) {
    return reports.stream()
      .map(report -> Pair.of(report, reportAuthorizationService.getAuthorizedRole(userId, report)))
      .filter(reportAndRole -> reportAndRole.getValue().isPresent())
      .map(reportAndRole -> new AuthorizedReportDefinitionDto(reportAndRole.getKey(), reportAndRole.getValue().get()))
      .collect(Collectors.toList());
  }

  private static void copyDefinitionMetaDataToUpdate(ReportDefinitionDto from,
                                                     ReportDefinitionUpdateDto to,
                                                     String userId) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setLastModifier(userId);
    to.setLastModified(from.getLastModified());
  }

  @FunctionalInterface
  private interface CreateReportMethod<RD extends ReportDataDto> {
    IdDto create(String userId, RD reportData, String reportName, String collectionId);
  }

}
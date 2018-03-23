package org.camunda.optimize.service.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class SharingServiceIT extends AbstractSharingIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Test
  public void dashboardWithoutReportsShare() {
    //given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();

    //then
    assertThat(response.getStatus(), is(200));
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    List<ReportLocationDto> reportLocations = dashboardShareDto.getReports();
    assertThat(reportLocations.size(), is(0));
  }

  @Test
  public void dashboardsWithDuplicateReportsAreShared() throws Exception {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId);

    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();

    //then
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    List<ReportLocationDto> reportLocation = dashboardShareDto.getReports();
    assertThat(reportLocation.size(), is(2));
    assertThat(reportLocation.get(0).getPosition().getX(), is(not(reportLocation.get(1).getPosition().getX())));
  }

  @Test
  public void individualReportShareIsNotAffectedByDashboard() throws Exception {
    //given
    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String reportShareId = addShareForReport(reportId2);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();
    assertThat(response.getStatus(), is(204));

    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(reportShareId))
        .request()
        .get();
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    // then
    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void canEvaluateEveryReportOfSharedDashboard() throws Exception {
    //given
    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();
    assertThat(response.getStatus(), is(204));

    // then
    response =
      embeddedOptimizeRule.target(getSharedDashboardReportEvaluationPath(dashboardShareId, reportId))
        .request()
        .get();
    assertThat(response.getStatus(), is(200));
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);
    assertReportData(reportId, evaluatedReportAsMap);

    response =
      embeddedOptimizeRule.target(getSharedDashboardReportEvaluationPath(dashboardShareId, reportId2))
        .request()
        .get();

    assertThat(response.getStatus(), is(200));
    evaluatedReportAsMap = response.readEntity(HashMap.class);
    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void sharedDashboardReportsCannotBeEvaluateViaSharedReport() throws Exception {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(reportId))
        .request()
        .get();
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void evaluateUnknownReportOfSharedDashboardThrowsError() throws Exception {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    String dashboardShareId = addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardReportEvaluationPath(dashboardShareId, FAKE_REPORT_ID))
        .request()
        .get();
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void evaluateUnknownSharedDashboardThrowsError() throws Exception {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardReportEvaluationPath("fakeDashboardShareId", reportId))
        .request()
        .get();
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void reportSharesOfDashboardsAreIndependent() throws Exception {
    //given
    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String dashboardId2 = addEmptyDashboardToOptimize();
    assertThat(dashboardId, is(not(dashboardId2)));
    addReportToDashboard(dashboardId2, reportId, reportId2);
    String dashboardShareId2 = addShareForDashboard(dashboardId2);

    // when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId2))
        .request()
        .get();
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    assertThat(dashboardShareDto.getReports().size(), is(2));

    response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();
    assertThat(response.getStatus(), is(204));

    //then
    response =
        embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
            .request()
            .get();

    assertThat(response.getStatus(), is(500));

    response =
        embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId2))
            .request()
            .get();
    dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    assertThat(dashboardShareDto.getReports().size(), is(2));
  }

  @Test
  public void removingReportFromDashboardRemovesRespectiveShare() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardWithReport);
    updateDashboard(dashboardWithReport, fullBoard);

    //then
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    assertThat(dashboardShareDto.getReports().size(), is(0));
  }

  @Test
  public void addingReportToDashboardAddsRespectiveShare() throws Exception {
    //given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    //when
    String reportId = createReport();
    addReportToDashboard(dashboardId, reportId);

    //then
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    assertThat(dashboardShareDto.getReports().size(), is(1));
  }

  @Test
  public void unsharedDashboardRemovesNotStandaloneReportShares() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);
    String reportShareId = addShareForReport(reportId);

    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);
    String dashboardReportShareId = dashboardShareDto.getReports().get(0).getId();

    // when
    response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();
    assertThat(response.getStatus(), is(204));

    //then
    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(dashboardReportShareId))
        .request()
        .get();
    assertThat(response.getStatus(), is(500));

    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(reportShareId))
        .request()
        .get();
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void cannotEvaluateDashboardOverReportsEndpoint() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    Response response =
        embeddedOptimizeRule.target(getSharedReportEvaluationPath(dashboardShareId))
            .request()
            .get();

    //then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void createNewFakeReportShareThrowsError() {

    // when
    Response response = createReportShareResponse(createReportShare());

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantCreateDashboardReportShare() {
    //given
    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(FAKE_REPORT_ID);

    // when
    Response response = createReportShareResponse(sharingDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewFakeDashboardShareThrowsError() {
    //given
    DashboardShareDto dashboardShare = new DashboardShareDto();
    dashboardShare.setDashboardId(FAKE_REPORT_ID);

    // when
    Response response = createDashboardShareResponse(dashboardShare);

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() throws Exception {
    //given
    String reportId = createReport();
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = createReportShareResponse(share);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));

    response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(share));

    assertThat(id, is(response.readEntity(String.class)));
  }

  @Test
  public void cantEvaluateNotExistingReportShare() {
    //when
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(FAKE_REPORT_ID))
        .request()
        .get();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantEvaluateNotExistingDashboardShare() {

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(FAKE_REPORT_ID))
        .request()
        .get();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantEvaluateUnsharedReport() throws Exception {
    //given
    String reportId = createReport();
    String shareId = this.addShareForReport(reportId);

    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(shareId))
        .request()
        .get();
    assertThat(response.getStatus(),is(200));

    //when
    response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/" + shareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();
    assertThat(response.getStatus(),is(204));

    //then
    response =
        embeddedOptimizeRule.target(getSharedReportEvaluationPath(shareId))
            .request()
            .get();
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void newIdGeneratedAfterDeletion() throws Exception {
    String reportId = createReport();
    String reportShareId = this.addShareForReport(reportId);

    //when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/" + reportShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();
    assertThat(response.getStatus(),is(204));

    String newShareId = this.addShareForReport(reportId);
    assertThat(reportShareId,is(not(newShareId)));
  }

  @Test
  public void sharesRemovedOnReportDeletion() throws Exception {
    //given
    String reportId = createReport();
    this.addShareForReport(reportId);

    // when
    embeddedOptimizeRule.target("report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .delete();

    //then
    ReportShareDto share = getShareForReport(reportId);
    assertThat(share, is(nullValue()));
  }

  @Test
  public void canEvaluateSharedReportWithoutAuthentication() throws Exception {
    // given
    String reportId = createReport();

    String shareId = addShareForReport(reportId);

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(shareId))
        .request()
        .get();
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    //then
    assertThat(response.getStatus(), is(200));
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void canCheckDashboardSharingStatus() throws Exception {
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);

    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    ShareSearchDto statusRequest = new ShareSearchDto();
    statusRequest.getDashboards().add(dashboardWithReport);
    statusRequest.getReports().add(reportId);

    dashboardWithReport = createDashboardWithReport(reportId);
    statusRequest.getDashboards().add(dashboardWithReport);
    //when

    Response response =
      embeddedOptimizeRule.target(SHARE + "/status")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(statusRequest));

    //then
    assertThat(response.getStatus(), is(200));
    ShareSearchResultDto result = response.readEntity(ShareSearchResultDto.class);

    assertThat(result.getDashboards().size(), is(2));
    assertThat(result.getDashboards().get(0).isShared(), is(true));
    assertThat(result.getDashboards().get(1).isShared(), is(false));

    assertThat(result.getReports().size(), is(1));
    assertThat(result.getReports().get(0).isShared(), is(false));
  }

  @Test
  public void canCheckReportSharingStatus() throws Exception {
    String reportId = createReport();
    String reportShareId = addShareForReport(reportId);

    ShareSearchDto statusRequest = new ShareSearchDto();
    statusRequest.getReports().add(reportId);
    reportId = createReport();
    statusRequest.getReports().add(reportId);

    //when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/status")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(statusRequest));

    //then
    assertThat(response.getStatus(), is(200));
    ShareSearchResultDto result = response.readEntity(ShareSearchResultDto.class);
    assertThat(result.getReports().size(), is(2));
    assertThat(result.getReports().get(0).isShared(), is(true));
    assertThat(result.getReports().get(1).isShared(), is(false));
  }

  @Test
  public void canCreateReportShareIfDashboardIsShared() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    String reportShareId = addShareForReport(reportId);

    //then
    assertThat(reportShareId, is(notNullValue()));

    ReportShareDto findApiReport = getShareForReport(reportId);
    assertThat(dashboardShareId, is(not(findApiReport.getId())));
  }

  @Test
  public void errorMessageIsWellStructured () throws Exception {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = this.createNewReport();
    ReportDataDto reportData = ReportDataHelper
      .createCountFlowNodeFrequencyGroupByFlowNoneNumber(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
      );
    reportData.setView(null);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    updateReport(reportId, report);

    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();

    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);

    response =
      embeddedOptimizeRule.target(
        getSharedDashboardReportEvaluationPath(
          dashboardShareId,
          dashboardShareDto.getReports().get(0).getId()
        )
      )
        .request()
        .get();
    //then
    assertThat(response.getStatus(), is(500));
    AbstractSharingIT.assertErrorFields(response.readEntity(ReportEvaluationException.class));
  }

}

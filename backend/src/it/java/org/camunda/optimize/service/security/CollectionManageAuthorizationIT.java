/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class CollectionManageAuthorizationIT extends AbstractCollectionRoleIT {

  private static final String USER_ID_JOHN = "John";

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateNameByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final PartialCollectionUpdateDto collectionRenameDto = new PartialCollectionUpdateDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityIsRejectedToUpdateNameByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final PartialCollectionUpdateDto collectionRenameDto = new PartialCollectionUpdateDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanUpdateNameByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    final PartialCollectionUpdateDto collectionRenameDto = new PartialCollectionUpdateDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void noRoleUserIsRejectedToUpdateName() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    final PartialCollectionUpdateDto collectionRenameDto = new PartialCollectionUpdateDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanCreateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    final CollectionRoleDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddRoleToCollectionRequest(collectionId, collectionRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToCreateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    final CollectionRoleDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddRoleToCollectionRequest(collectionId, collectionRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanCreateRoleByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final CollectionRoleDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddRoleToCollectionRequest(collectionId, collectionRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = addRoleToCollectionAsDefaultUser(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateRoleToCollectionRequest(collectionId, roleId, new CollectionRoleUpdateDto(RoleType.MANAGER))
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToUpdateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final CollectionRoleDto johnEditorRoleDto = createJohnEditorRoleDto();
    final String roleId = addRoleToCollectionAsDefaultUser(collectionId, johnEditorRoleDto);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateRoleToCollectionRequest(collectionId, roleId, new CollectionRoleUpdateDto(RoleType.MANAGER))
      .execute();
    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanUpdateRoleByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = addRoleToCollectionAsDefaultUser(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateRoleToCollectionRequest(collectionId, roleId, new CollectionRoleUpdateDto(RoleType.MANAGER))
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanDeleteRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = addRoleToCollectionAsDefaultUser(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteRoleToCollectionRequest(collectionId, roleId)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToDeleteRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = addRoleToCollectionAsDefaultUser(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteRoleToCollectionRequest(collectionId, roleId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanDeleteRoleByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = addRoleToCollectionAsDefaultUser(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteRoleToCollectionRequest(collectionId, roleId)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanCreateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddScopeEntryToCollectionRequest(collectionId, createProcessScope())
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToCreateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddScopeEntryToCollectionRequest(collectionId, createProcessScope())
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanCreateScopeByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddScopeEntryToCollectionRequest(collectionId, createProcessScope())
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    final String scopeEntryId = addScopeToCollectionAsDefaultUser(collectionId, createProcessScope());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, createScopeUpdate())
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToUpdateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    final String scopeEntryId = addScopeToCollectionAsDefaultUser(collectionId, createProcessScope());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, createScopeUpdate())
      .execute();
    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanUpdateScopeByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    final String scopeEntryId = addScopeToCollectionAsDefaultUser(collectionId, createProcessScope());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, createScopeUpdate())
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanDeleteScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    final String scopeEntryId = addScopeToCollectionAsDefaultUser(collectionId, createProcessScope());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToDeleteScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    final String scopeEntryId = addScopeToCollectionAsDefaultUser(collectionId, createProcessScope());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityCanDeleteScopeByCollectionRole() {
    //given
    final String collectionId = createNewCollectionAsDefaultUser();
    final String scopeEntryId = addScopeToCollectionAsDefaultUser(collectionId, createProcessScope());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
  }

  private CollectionScopeEntryUpdateDto createScopeUpdate() {
    return new CollectionScopeEntryUpdateDto(Collections.singletonList("1"), Collections.singletonList("tenant1"));
  }

  private CollectionScopeEntryDto createProcessScope() {
    return new CollectionScopeEntryDto(DefinitionType.PROCESS, "KEY");
  }

  private String addScopeToCollectionAsDefaultUser(final String collectionId,
                                                   final CollectionScopeEntryDto scopeEntryDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, scopeEntryDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private CollectionRoleDto createJohnEditorRoleDto() {
    return new CollectionRoleDto(new IdentityDto(USER_ID_JOHN, IdentityType.USER), RoleType.EDITOR);
  }

}

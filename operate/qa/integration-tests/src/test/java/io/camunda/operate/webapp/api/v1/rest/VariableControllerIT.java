/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.SearchController.SEARCH;
import static io.camunda.operate.webapp.api.v1.rest.VariableController.URI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestApplicationWithNoBeans.class, VariableController.class})
public class VariableControllerIT {

  @Autowired WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean private VariableDao variableDao;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldAcceptEmptyJSONQuery() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "{}");
    verify(variableDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "");
    verify(variableDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"searchAfter\": [\"" + Variable.PROCESS_INSTANCE_KEY + "\"], \"size\": 7}");
    verify(variableDao)
        .search(
            new Query<Variable>()
                .setSearchAfter(new Object[] {Variable.PROCESS_INSTANCE_KEY})
                .setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"size\": 7, \"sort\": [ " + getSortFor(Variable.NAME, Order.DESC) + " ] }");
    verify(variableDao)
        .search(new Query<Variable>().setSize(7).setSort(Sort.listOf(Variable.NAME, Order.DESC)));
  }

  private String getSortFor(final String field, final Query.Sort.Order order) {
    return "{ \"field\":\"" + field + "\", \"order\": \"" + order.name() + "\" }";
  }

  @Test
  public void shouldAcceptQueryWithFilter() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH, "{\"filter\": { \"" + Variable.NAME + "\": \"customerId\" } }");
    verify(variableDao)
        .search(new Query<Variable>().setFilter(new Variable().setName("customerId")));
  }

  @Test
  public void shouldAcceptQueryWithFullFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"filter\": "
            + "{ "
            + "\"processInstanceKey\": 235 ,"
            + "\"name\": \"customerId\", "
            + "\"key\": 4217, "
            + "\"tenantId\": \"tenantA\""
            + "},"
            + "\"size\": 17, "
            + "\"sort\": ["
            + getSortFor(Variable.NAME, Order.DESC)
            + "]"
            + "}");
    verify(variableDao)
        .search(
            new Query<Variable>()
                .setFilter(
                    new Variable()
                        .setKey(4217L)
                        .setName("customerId")
                        .setProcessInstanceKey(235L)
                        .setTenantId("tenantA"))
                .setSort(Sort.listOf(Variable.NAME, Order.DESC))
                .setSize(17));
  }

  @Test
  public void shouldReturnErrorMessageForListFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":500,\"message\":\"Error in retrieving data.\",\"instance\":\"47a7e1e4-5f09-4086-baa0-c9bcd40da029\",\"type\":\"API application error\"}";
    // given
    when(variableDao.search(any(Query.class)))
        .thenThrow(
            new ServerException("Error in retrieving data.")
                .setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(URI + SEARCH, "{}")
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByKeyFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":404,\"message\":\"Error in retrieving data for key.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(variableDao.byKey(any(Long.class)))
        .thenThrow(
            new ResourceNotFoundException("Error in retrieving data for key.")
                .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235")
        .andExpect(status().isNotFound())
        .andExpect(content().string(expectedJSONContent));
  }

  protected ResultActions assertGetWithFailed(final String endpoint) throws Exception {
    return mockMvc
        .perform(get(endpoint))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE));
  }

  protected ResultActions assertPostToWithFailed(final String endpoint, final String content)
      throws Exception {
    return mockMvc.perform(post(endpoint).content(content).contentType(MediaType.APPLICATION_JSON));
  }

  protected ResultActions assertPostToWithSucceed(final String endpoint, final String content)
      throws Exception {
    return mockMvc
        .perform(post(endpoint).content(content).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}

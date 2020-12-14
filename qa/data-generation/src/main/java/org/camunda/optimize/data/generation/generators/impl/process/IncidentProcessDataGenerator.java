/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class IncidentProcessDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/incident-process.bpmn";

  public IncidentProcessDataGenerator(final SimpleEngineClient engineClient,
                                      final Integer nVersions,
                                      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    String[] incidentType = new String[]{"noIncident", "incidentToBeResolved", "incident"};
    Map<String, Object> variables = new HashMap<>();
    variables.put(
      "incidentType",
      incidentType[ThreadLocalRandom.current().nextInt(0, incidentType.length)]
    );
    return variables;
  }

  @Override
  protected boolean createsIncidents() {
    return true;
  }
}

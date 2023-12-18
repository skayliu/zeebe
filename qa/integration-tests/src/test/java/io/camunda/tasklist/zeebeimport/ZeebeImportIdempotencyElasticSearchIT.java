/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.TestUtil;
import io.camunda.tasklist.util.apps.idempotency.ZeebeImportIdempotencyElasticSearchTestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that even if the Zeebe data is imported twice, in Tasklist Elasticsearch is is still
 * consistent.
 */
@SpringBootTest(
    classes = {ZeebeImportIdempotencyElasticSearchTestConfig.class, TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.main.allow-bean-definition-overriding=true",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
      "graphql.servlet.exception-handlers-enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZeebeImportIdempotencyElasticSearchIT extends ZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyElasticSearchTestConfig.CustomElasticsearchBulkProcessor
      elasticsearchBulkProcessor;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Override
  protected void processAllRecordsAndWait(TestCheck waitTill, Object... arguments) {
    databaseTestExtension.processAllRecordsAndWait(waitTill, arguments);
    databaseTestExtension.processAllRecordsAndWait(waitTill, arguments);
    elasticsearchBulkProcessor.cancelAttempts();
  }
}

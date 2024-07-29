/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.spring.client.configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.CamundaClient;
import io.camunda.zeebe.spring.client.event.ZeebeLifecycleEventProducer;
import io.camunda.zeebe.spring.client.testsupport.SpringZeebeTestContext;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Enabled by META-INF of Spring Boot Starter to provide beans for Camunda Clients */
@Configuration
@ImportAutoConfiguration({
  ZeebeClientProdAutoConfiguration.class,
  ZeebeClientAllAutoConfiguration.class,
  ZeebeActuatorConfiguration.class,
  MetricsDefaultConfiguration.class,
  JsonMapperConfiguration.class
})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class CamundaAutoConfiguration {

  public static final ObjectMapper DEFAULT_OBJECT_MAPPER =
      new ObjectMapper()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

  @Bean
  @ConditionalOnMissingBean(
      SpringZeebeTestContext
          .class) // only run if we are not running in a test case - as otherwise the lifecycle
  // is controlled by the test
  public ZeebeLifecycleEventProducer zeebeLifecycleEventProducer(
      final CamundaClient client, final ApplicationEventPublisher publisher) {
    return new ZeebeLifecycleEventProducer(client, publisher);
  }
}

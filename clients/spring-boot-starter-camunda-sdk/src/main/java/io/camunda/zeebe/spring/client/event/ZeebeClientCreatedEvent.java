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
package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.client.CamundaClient;
import org.springframework.context.ApplicationEvent;

/**
 * Event which is triggered when the CamundaClient was created. This can be used to register further
 * work that should be done, like starting job workers or doing deployments.
 *
 * <p>In a normal production application this event is simply fired once during startup when the
 * CamundaClient is created and thus ready to use. However, in test cases it might be fired multiple
 * times, as every test case gets its own dedicated engine also leading to new ZeebeClients being
 * created (at least logically, as the CamundaClient Spring bean might simply be a proxy always
 * pointing to the right client automatically to avoid problems with @Autowire).
 *
 * <p>Furthermore, when `zeebe.client.enabled=false`, the event might not be fired ever
 */
public class ZeebeClientCreatedEvent extends ApplicationEvent {

  public final CamundaClient client;

  public ZeebeClientCreatedEvent(final Object source, final CamundaClient client) {
    super(source);
    this.client = client;
  }

  public CamundaClient getClient() {
    return client;
  }
}

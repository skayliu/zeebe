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
package io.camunda.zeebe.spring.client.properties.common;

import io.camunda.identity.sdk.IdentityConfiguration.Type;

public class AuthProperties {

  // simple
  private String username;
  private String password;

  // oidc and saas
  private String clientId;
  private String clientSecret;

  private Type oidcType;
  private String issuer;

  public Type getOidcType() {
    return oidcType;
  }

  public void setOidcType(final Type oidcType) {
    this.oidcType = oidcType;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(final String issuer) {
    this.issuer = issuer;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }
}

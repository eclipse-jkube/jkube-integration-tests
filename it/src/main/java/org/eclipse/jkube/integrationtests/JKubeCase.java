/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.RequestConfigBuilder;
import io.fabric8.kubernetes.client.http.HttpResponse;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Report;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Report
public interface JKubeCase {

  String JKUBE_VERSION_SYSTEM_PROPERTY = "jkubeVersion";

  // TODO: Move to KubernetesCase interface
  KubernetesClient getKubernetesClient();

  default String getApplication() {
    return getClass().getAnnotation(Application.class).value();
  }

  // TODO: Move to KubernetesCase interface
  default HttpResponse<String> httpGet(String uri) throws InterruptedException, ExecutionException, TimeoutException {
    final var requestConfig = new RequestConfigBuilder()
      .withRequestRetryBackoffLimit(0)
      .build();
    final var client = getKubernetesClient().getHttpClient().newBuilder().tag(requestConfig).build();
    return client.sendAsync(
      client.newHttpRequestBuilder()
      .uri(uri)
      .build(), String.class)
      .get(5, TimeUnit.SECONDS);
  }
}

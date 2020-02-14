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
package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.net.MalformedURLException;
import java.net.URL;

public class KubernetesClientAssertion<T extends KubernetesResource> {

  private static OkHttpClient okHttpClient;

  synchronized static OkHttpClient httpClient() {
    if (okHttpClient == null) {
      okHttpClient = new OkHttpClient.Builder().build();
    }
    return okHttpClient;
  }

  private final JKubeCase jKubeCase;
  private final T kubernetesResource;

  KubernetesClientAssertion(JKubeCase jKubeCase, T kubernetesResource) {
    this.jKubeCase = jKubeCase;
    this.kubernetesResource = kubernetesResource;
  }

  final String getClusterHost() throws MalformedURLException {
    return new URL(jKubeCase.getKubernetesClient().getConfiguration().getMasterUrl()).getHost();
  }

  public T getKubernetesResource() {
    return kubernetesResource;
  }

  public KubernetesClient getKubernetesClient() {
    return jKubeCase.getKubernetesClient();
  }

  public String getApplication() {
    return jKubeCase.getApplication();
  }
}

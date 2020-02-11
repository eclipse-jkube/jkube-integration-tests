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

  final KubernetesClient kubernetesClient;
  final T kubernetesResource;

  KubernetesClientAssertion(KubernetesClient kubernetesClient, T kubernetesResource) {
    this.kubernetesClient = kubernetesClient;
    this.kubernetesResource = kubernetesResource;
  }

  final String getClusterHost() throws MalformedURLException {
    return new URL(kubernetesClient.getConfiguration().getMasterUrl()).getHost();
  }
}

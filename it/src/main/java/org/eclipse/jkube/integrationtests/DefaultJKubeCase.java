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

public class DefaultJKubeCase implements JKubeCase {

  private final String application;
  private final KubernetesClient kubernetesClient;

  public DefaultJKubeCase(String application, KubernetesClient kubernetesClient) {
    this.application = application;
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public String getApplication() {
    return application;
  }
}

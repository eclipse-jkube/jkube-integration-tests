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
package org.eclipse.jkube.integrationtests.springboot.helmconfig;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;

@KubernetesTest(createEphemeralNamespace = false)
abstract class HelmConfig implements JKubeCase, MavenCase {
  private static final String PROJECT_HELM = "projects-to-be-tested/maven/spring/helm-config";

  private static KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public String getProject() {
    return PROJECT_HELM;
  }
}

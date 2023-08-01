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
package org.eclipse.jkube.integrationtests.springboot.zeroconfigfatjar;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.hasSize;

@KubernetesTest(createEphemeralNamespace = false)
public class ZeroConfigFatJar implements JKubeCase, MavenCase {
  private static final String PROJECT_ZERO_CONFIG_FATJAR = "projects-to-be-tested/maven/spring/zero-config-fatjar";

  private static KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public String getProject() {
    return PROJECT_ZERO_CONFIG_FATJAR;
  }

  @Override
  public String getApplication() {
    return "spring-boot-zero-config-fatjar";
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started ZeroConfigFatJarApplication in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, false);
    return pod;
  }
}

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
package org.eclipse.jkube.integrationtests.openliberty;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;

@KubernetesTest(createEphemeralNamespace = false)
abstract class OpenLiberty implements JKubeCase, MavenCase {

  static final String PROJECT_OPENLIBERTY = "projects-to-be-tested/maven/openliberty/rest";
  private static final int LOG_TIMEOUT = 60;

  private static KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public String getProject() {
    return PROJECT_OPENLIBERTY;
  }

  @Override
  public String getApplication() {
    return "openliberty-rest";
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains(String.format("Application %s started",getApplication()), LOG_TIMEOUT);
    assertPod(pod).apply(this).logContains(
      "The defaultServer server is ready to run a smarter planet. The defaultServer server started in",
      LOG_TIMEOUT);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertPorts(hasSize(1))
      .assertPort("glrpc", 9080, true)
      .assertNodePortResponse("glrpc", equalTo("Hello, World."));
    return pod;
  }

  final void assertLog(String log) {
    assertThat(log,
      stringContainsInOrder(
        "The defaultServer server started",
        "Monitoring dropins for applications",
        "Web application available (default_host)",
        String.format("Application %s started in", getApplication())
      ));
  }
}

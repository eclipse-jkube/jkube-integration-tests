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

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

abstract class OpenLiberty extends BaseMavenCase implements JKubeCase {

  static final String PROJECT_OPENLIBERTY = "projects-to-be-tested/openliberty/rest";
  private static final int LOG_TIMEOUT = 60;

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
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("glrpc", 9080, true)
      .assertNodePortResponse("glrpc", equalTo("Hello, World."));
    return pod;
  }
}

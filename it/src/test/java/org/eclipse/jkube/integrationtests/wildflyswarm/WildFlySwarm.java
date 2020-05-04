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
package org.eclipse.jkube.integrationtests.wildflyswarm;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

abstract class WildFlySwarm extends BaseMavenCase implements JKubeCase {
  static final String PROJECT_WILDFLYSWARM="projects-to-be-tested/wildflyswarm/rest";

  @Override
  public String getProject() {
    return PROJECT_WILDFLYSWARM;
  }

  @Override
  public String getApplication() {
    return "wildflyswarm-rest";
  }

  final Pod assertThatShouldApplyResources()  throws  Exception{
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Deployed", 120);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", equalTo("Hello from JKube!"),"/wildflyswarm-rest");
    return pod;
  }
}

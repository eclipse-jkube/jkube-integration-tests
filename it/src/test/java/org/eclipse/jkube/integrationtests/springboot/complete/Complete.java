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
package org.eclipse.jkube.integrationtests.springboot.complete;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.assertions.ServiceAssertion;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.hasSize;

abstract class Complete extends BaseMavenCase implements JKubeCase {

  private static final String PROJECT_COMPLETE = "projects-to-be-tested/spring-boot/complete";

  @Override
  public String getProject() {
    return PROJECT_COMPLETE;
  }

  @Override
  public String getApplication() {
    return "spring-boot-complete";
  }

  final ServiceAssertion assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("CompleteApplication   : Started CompleteApplication in", 60);
    return awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertPorts(hasSize(1))
      .assertPort("us-cli", 8082, true);
  }

}

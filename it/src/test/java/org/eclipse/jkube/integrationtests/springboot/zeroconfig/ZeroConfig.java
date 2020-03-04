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
package org.eclipse.jkube.integrationtests.springboot.zeroconfig;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import java.io.IOException;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.hasSize;

abstract class ZeroConfig extends BaseMavenCase implements JKubeCase {

  static final String PROJECT_ZERO_CONFIG = "projects-to-be-tested/spring-boot/zero-config";

  @Override
  public String getProject() {
    return PROJECT_ZERO_CONFIG;
  }

  @Override
  public String getApplication() {
    return "spring-boot-zero-config";
  }

  final Pod assertThatShouldApplyResources() throws InterruptedException, IOException {
    final Pod pod = awaitPod(this)
      .logContains("Started ZeroConfigApplication in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, false);
    return pod;
  }

}

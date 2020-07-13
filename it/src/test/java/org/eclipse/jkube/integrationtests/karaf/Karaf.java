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
package org.eclipse.jkube.integrationtests.karaf;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;


import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;

abstract class Karaf extends BaseMavenCase implements JKubeCase {

  static String PROJECT_KARAF = "projects-to-be-tested/karaf/camel-log";
  private static final int LOG_TIMEOUT = 120;

  @Override
  public String getProject() { return PROJECT_KARAF;}

  @Override
  public String getApplication() {return  "karaf-camel-log";}

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Hello from Camel!",LOG_TIMEOUT);
    awaitService(this,pod.getMetadata().getNamespace())
      .assertIsClusterIp()
      .assertPorts(hasSize(1))
      .assertPort("intermapper",8181,false);
    return pod;
  }

  final void assertLog(String log) {
    assertThat(log,
      stringContainsInOrder(
        "Hello from Camel!",
        "My id is ID-karaf-camel-log")
      );
  }
}

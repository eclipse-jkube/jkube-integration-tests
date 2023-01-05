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
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.jupiter.api.TempKubernetesTest;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.core.AllOf.allOf;

@TempKubernetesTest
abstract class Karaf implements MavenCase, JKubeCase {

  private static final String PROJECT_KARAF = "projects-to-be-tested/maven/karaf/camel-log";
  private static final int LOG_TIMEOUT = 60;

  private static KubernetesClient kubernetesClient;

  @TempDir
  Path tempDir;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public String getProject() { return PROJECT_KARAF;}

  @Override
  public String getApplication() {return  "karaf-camel-log";}

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this)
      .logContains("Hello from Camel!", LOG_TIMEOUT)
      .logContains("Generating order order1.xml", LOG_TIMEOUT)
      .logContains("Processing order order1.xml", LOG_TIMEOUT)
      .logContains("Generating order order2.xml", LOG_TIMEOUT);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsClusterIp()
      .assertPorts(hasSize(1))
      .assertPort("intermapper",8181,false);
    assertOrders(pod);
    return pod;
  }

  final void assertLog(String log) {
    assertThat(log, allOf(
      stringContainsInOrder("Hello from Camel!", "My id is ID-karaf-camel-log"),
      containsString("Generating order order1.xml")
    ));
  }

  final void assertOrders(Pod pod) throws IOException {
    final Path order1 = tempDir.resolve("order1.xml");
    getKubernetesClient().pods()
      .inNamespace(pod.getMetadata().getNamespace())
      .withName(pod.getMetadata().getName())
      .file("/deployments/karaf/work/orders/input/.camel/order1.xml")
      .copy(order1);
    assertThat(Files.readString(order1), allOf(
      containsString("<customer id=\""),
      containsString("<orderlines>"),
      containsString("<orderline>"),
      containsString("<description>")
    ));
  }
}

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
package org.eclipse.jkube.integrationtests.springboot.javaversion;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.cli.CliUtils.runCommand;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Disabled("Pending jkube.java.version support — eclipse-jkube/jkube#3931")
@Tag(KUBERNETES)
@Application("sb-zero-config")
@TestMethodOrder(OrderAnnotation.class)
@KubernetesTest(createEphemeralNamespace = false)
class JavaVersionK8sGradleITCase implements JKubeCase {

  @Gradle(project = "sb-zero-config")
  private JKubeGradleRunner gradle;

  private KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Test
  @Order(1)
  @DisplayName("k8sBuild with jkube.java.version=21, should use jkube-java-21 base image")
  void k8sBuild() {
    // When
    final var result = gradle.tasks("-Pjkube.java.version=21", "k8sBuild").build();
    // Then
    assertThat(result.getOutput(), containsString("jkube-java-21"));
  }

  @Test
  @Order(1)
  @DisplayName("k8sResource, should create manifests")
  void k8sResource() {
    gradle.tasks("k8sResource").build();
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8sApply, should deploy pod with JDK 21")
  void k8sApply() throws Exception {
    // When
    gradle.tasks("k8sApply").build();
    // Then
    final Pod pod = awaitPod(this)
      .logContains("Started", 60)
      .getKubernetesResource();
    final String namespace = pod.getMetadata().getNamespace();
    final var javaVersion = runCommand(
      "kubectl run jkube-java-version-check --rm -i --image=gradle/"
        + getApplication() + ":latest --restart=Never --image-pull-policy=Never -n "
        + namespace + " --command -- java -version");
    assertThat(javaVersion.getOutput(), containsString("\"21"));
  }

  @Test
  @Order(3)
  @DisplayName("k8sUndeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    gradle.tasks("k8sUndeploy").build();
    // Then
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }
}

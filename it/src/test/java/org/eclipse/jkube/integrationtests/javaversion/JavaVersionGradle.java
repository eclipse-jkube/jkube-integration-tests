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
package org.eclipse.jkube.integrationtests.javaversion;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Disabled("Pending jkube.java.version support — eclipse-jkube/jkube#3931")
@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
@KubernetesTest(createEphemeralNamespace = false)
public abstract class JavaVersionGradle implements JKubeCase {

  private KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  protected abstract JKubeGradleRunner getGradle();

  protected String getLogMarker() {
    return null;
  }

  @Test
  @Order(1)
  @DisplayName("k8sBuild with jkube.java.version=21, should use jkube-java-21 base image")
  protected void k8sBuild() {
    // When
    final var result = getGradle().tasks("-Pjkube.java.version=21", "k8sBuild").build();
    // Then
    assertThat(result.getOutput(), containsString("jkube-java-21"));
  }

  @Test
  @Order(1)
  @DisplayName("k8sResource, should create manifests")
  protected void k8sResource() {
    getGradle().tasks("k8sResource").build();
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8sApply, should deploy pod with JDK 21")
  protected void k8sApply() throws Exception {
    // When
    getGradle().tasks("k8sApply").build();
    // Then
    var podAssertion = awaitPod(this);
    if (getLogMarker() != null) {
      podAssertion.logContains(getLogMarker(), 60);
    }
    final Pod pod = podAssertion.getKubernetesResource();
    final String namespace = pod.getMetadata().getNamespace();
    final var javaVersion = runCommand(
      "kubectl run jkube-jv-check-" + getApplication() + " --rm -i --image=gradle/"
        + getApplication() + ":latest --restart=Never --image-pull-policy=Never -n "
        + namespace + " --command -- java -version");
    assertThat(javaVersion.getExitCode(), equalTo(0));
    assertThat(javaVersion.getOutput(), containsString("\"21"));
  }

  @Test
  @Order(3)
  @DisplayName("k8sUndeploy, should delete all applied resources")
  protected void k8sUndeploy() throws Exception {
    // When
    getGradle().tasks("k8sUndeploy").build();
    // Then
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }

  // Runs after undeploy — local-only build, no cluster interaction
  @Test
  @Order(4)
  @DisplayName("k8sBuild without jkube.java.version, should use default jkube-java base image")
  protected void k8sBuildDefault() {
    // When
    final var result = getGradle().tasks("k8sBuild").build();
    // Then
    assertThat(result.getOutput(), allOf(
      containsString("jkube-java"),
      not(containsString("jkube-java-11")),
      not(containsString("jkube-java-17")),
      not(containsString("jkube-java-21")),
      not(containsString("jkube-java-25"))
    ));
  }
}

/*
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
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

// TODO(eclipse-jkube/jkube#3931): remove @Disabled once merged
@Disabled("Pending jkube.java.version support — eclipse-jkube/jkube#3931")
@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
@KubernetesTest(createEphemeralNamespace = false)
public abstract class JavaVersionMaven implements JKubeCase, MavenCase {

  private KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  protected String getLogMarker() {
    return null;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build with jkube.java.version=21, should use jkube-java-21 base image")
  protected void k8sBuild() throws Exception {
    // When
    final MavenInvocationResult result = maven("k8s:build",
      properties("jkube.java.version", "21"));
    // Then
    assertInvocation(result);
    assertThat(result.getStdOut(), containsString("jkube-java-21"));
  }

  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifests")
  protected void k8sResource() throws Exception {
    // When
    final MavenInvocationResult result = maven("k8s:resource");
    // Then
    assertInvocation(result);
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod with JDK 21")
  protected void k8sApply() throws Exception {
    // When
    final MavenInvocationResult result = maven("k8s:apply");
    // Then
    assertInvocation(result);
    var podAssertion = awaitPod(this);
    if (getLogMarker() != null) {
      podAssertion.logContains(getLogMarker(), 60);
    }
    final Pod pod = podAssertion.getKubernetesResource();
    final String namespace = pod.getMetadata().getNamespace();
    final var output = new ByteArrayOutputStream();
    try (ExecWatch ignored = getKubernetesClient().pods().inNamespace(namespace)
        .withName(pod.getMetadata().getName())
        .writingOutput(output).writingError(output)
        .exec("java", "-version")) {
      ignored.exitCode().get(30, TimeUnit.SECONDS);
    }
    assertThat(output.toString(StandardCharsets.UTF_8), containsString("version \"21"));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  protected void k8sUndeploy() throws Exception {
    // When
    final MavenInvocationResult result = maven("k8s:undeploy");
    // Then
    assertInvocation(result);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }

  // Runs after undeploy — local-only build, no cluster interaction
  @Test
  @Order(4)
  @DisplayName("k8s:build without jkube.java.version, should use default jkube-java base image")
  protected void k8sBuildDefault() throws Exception {
    // When
    final MavenInvocationResult result = maven("k8s:build");
    // Then
    assertInvocation(result);
    assertThat(result.getStdOut(), containsString("/jkube-java:"));
    assertThat(result.getStdOut(), not(matchesPattern("(?s).*jkube-java-\\d+.*")));
  }
}

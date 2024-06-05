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
package org.eclipse.jkube.integrationtests.buildpacks;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.getLabels;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listDockerVolumeNames;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@KubernetesTest(createEphemeralNamespace = false)
class BuildPacksBuildStrategyK8sITCase implements JKubeCase, MavenCase {
  private KubernetesClient kubernetesClient;
  private static final String PROJECT_BUILDPACKS_SIMPLE = "projects-to-be-tested/maven/buildpacks/simple";

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public String getApplication() {
    return "buildpacks-simple";
  }

  @Override
  public String getProject() {
    return PROJECT_BUILDPACKS_SIMPLE;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/buildpacks-simple-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/buildpacks-simple-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create container image using buildpacks")
  void k8sBuild() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertThat(invocationResult.getStdOut(), stringContainsInOrder(
      "Delegating container image building process to BuildPacks",
      "Using pack",
      "base: Pulling from paketobuildpacks/builder",
      "===> ANALYZING",
      "===> DETECTING",
      "===> EXPORTING",
      "Successfully built image 'integration-tests/buildpacks-simple:latest'",
      "[INFO] BUILD SUCCESS"
    ));
    Map<String, String> imageLabels = getLabels("integration-tests/buildpacks-simple:latest");
    assertThat(imageLabels, hasEntry("io.buildpacks.stack.id", "io.buildpacks.stacks.bionic"));
    assertThat(imageLabels, hasEntry("io.buildpacks.project.metadata", "{}"));
    assertThat(imageLabels, hasKey("io.buildpacks.build.metadata"));
    assertThat(imageLabels, hasKey("io.buildpacks.lifecycle.metadata"));
    assertThat(imageLabels, hasKey("io.buildpacks.stack.description"));
    assertThat(listDockerVolumeNames().stream()
      .filter(v -> v.endsWith(".build") || v.endsWith(".launch"))
      .filter(v -> v.contains("integration-tests_buildpacks-simple"))
      .collect(Collectors.toList()), hasSize(2));
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service")
  void k8sApply() throws Exception {
    // Given
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertInvocation(invocationResult);
    final Pod pod = awaitPod(this).getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", containsString("Simple Application containerized using buildpacks with JKube!"), "");
  }

  @Test
  @Order(3)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertInvocation(invocationResult);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }
}

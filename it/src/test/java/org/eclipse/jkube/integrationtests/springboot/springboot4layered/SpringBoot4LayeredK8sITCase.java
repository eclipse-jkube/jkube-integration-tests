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
package org.eclipse.jkube.integrationtests.springboot.springboot4layered;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.List;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.getImageHistory;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class SpringBoot4LayeredK8sITCase extends SpringBoot4Layered {

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create layered jar image with Spring Boot 4.1 tools jarmode")
  void k8sBuild() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    // Verify that tools jarmode with --layers flag is used (not layertools)
    assertThat(invocationResult.getStdOut(), containsString("extract --launcher --layers --destination"));
    assertImageWasRecentlyBuilt("integration-tests", getApplication());

    // Verify layered structure in image
    final List<String> imageFiles = listImageFiles("integration-tests/" + getApplication(), "/deployments");
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/lib"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/classes"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/classpath.idx"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/layers.idx"));
    assertThat(imageFiles, hasItem("/deployments/org/springframework/boot/loader/launch/JarLauncher.class"));

    // Verify Docker layers were created (should have multiple COPY layers for Spring Boot layers)
    final List<String> imageHistory = getImageHistory("integration-tests/" + getApplication());
    long dirCopyLayers = imageHistory.stream()
      .filter(l -> !l.startsWith("<missing>"))
      .filter(l -> l.contains("COPY dir:"))
      .count();
    // Should have multiple layers (dependencies, spring-boot-loader, application, etc.)
    assertThat(dirCopyLayers, equalTo(4L));
  }

  @Test
  @Order(2)
  @DisplayName("k8s:resource, should create Kubernetes manifests")
  void k8sResource() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
        String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/spring-boot-4-layered-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/spring-boot-4-layered-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and start Spring Boot application")
  void k8sApply() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertInvocation(invocationResult);
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Started SpringBoot4LayeredApplication", 60);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", containsString("Hello from Spring Boot 4.1 with Layered Jars!"));
  }

  @Test
  @Order(4)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertInvocation(invocationResult);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }
}

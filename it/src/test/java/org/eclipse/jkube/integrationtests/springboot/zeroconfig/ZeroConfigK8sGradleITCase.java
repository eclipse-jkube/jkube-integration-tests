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
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistryHost;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.springboot.zeroconfig.ZeroConfig.GRADLE_APPLICATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@Application(GRADLE_APPLICATION)
@DockerRegistry(port = 5015)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZeroConfigK8sGradleITCase extends ZeroConfig {

  @Gradle(project = "sb-zero-config")
  private JKubeGradleRunner gradle;

  @DockerRegistryHost
  private String registry;

  @Test
  @Order(1)
  @DisplayName("k8sResource, should create manifests")
  void k8sResource() {
    // When
    gradle.tasks("k8sResource").build();
    // Then
    var resourcePath = gradle.getModulePath().resolve("build").resolve("classes").resolve("java")
      .resolve("main").resolve("META-INF").resolve("jkube");
    assertThat(resourcePath.toFile(), anExistingDirectory());
    assertListResource(resourcePath.resolve("kubernetes.yml"));
    assertThat(resourcePath.resolve("kubernetes").resolve("sb-zero-config-deployment.yml").toFile(),
      yaml(not(anEmptyMap())));
    assertThat(resourcePath.resolve("kubernetes").resolve("sb-zero-config-service.yml").toFile(),
      yaml(not(anEmptyMap())));
  }
  @Test
  @Order(1)
  @DisplayName("k8sBuild, should create image")
  void k8sBuild() throws Exception {
    // When
    gradle.tasks("k8sBuild").build();
    // Then
    assertImageWasRecentlyBuilt("gradle", getApplication());
  }

  @Test
  @Order(2)
  @DisplayName("k8sPush, should push image to remote registry")
  void k8sPush() throws Exception {
    // When
    gradle.tasks("-Pjkube.docker.push.registry=" + registry, "k8sPush").build();
    // Then
    assertThat(httpGet("http://" + registry + "/v2/gradle/" + getApplication() + "/tags/list").body(),
      containsString("{\"name\":\"gradle/" + getApplication() + "\",\"tags\":[\"latest\"]}"));
  }

  @Test
  @Order(2)
  @DisplayName("k8sHelm, should create Helm charts")
  void k8sHelm() {
    // When
    gradle.tasks("k8sHelm").build();
    // Then
    final var helmDirectory = gradle.getModulePath().resolve("build").resolve("jkube")
      .resolve("helm").resolve(getApplication()).resolve("kubernetes");
    assertThat(helmDirectory.resolve(getApplication() + "-0.0.0-SNAPSHOT.tar.gz").toFile(),
      anExistingFile());
    assertThat(helmDirectory.resolve("Chart.yaml").toFile(), yaml(allOf(
      aMapWithSize(3),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", getApplication()),
      hasEntry("version", "0.0.0-SNAPSHOT")
    )));
    assertThat(helmDirectory.resolve("values.yaml").toFile(), yaml(anEmptyMap()));
    assertThat(helmDirectory.resolve("templates").resolve("sb-zero-config-service.yaml").toFile(),
      yaml(not(anEmptyMap())));
    assertThat(helmDirectory.resolve("templates").resolve("sb-zero-config-deployment.yaml").toFile(),
      yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8sApply, should deploy pod and service")
  @SuppressWarnings("unchecked")
  void k8sApply() throws Exception {
    // When
    gradle.tasks("k8sApply").build();
    // Then
    final Pod pod = awaitPod(this)
      .logContains("Started ZeroConfigApplication in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, false);
    awaitDeployment(this, pod.getMetadata().getNamespace())
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", equalTo("gradle/sb-zero-config:latest")),
        hasProperty("name", equalTo("spring-boot")),
        hasProperty("ports", hasSize(3)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("http")),
          hasProperty("containerPort", equalTo(8080))
        )))
      )));
  }

  @Test
  @Order(4)
  @DisplayName("k8sLog, should retrieve log")
  void k8sLog() {
    // When
    final var result = gradle.tasks("k8sLog", "-Pjkube.log.follow=false").build();
    // Then
    assertThat(result.getOutput(),
      stringContainsInOrder("Tomcat started on port(s): 8080", "Started ZeroConfigApplication in", "seconds"));
  }

  @Test
  @Order(5)
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

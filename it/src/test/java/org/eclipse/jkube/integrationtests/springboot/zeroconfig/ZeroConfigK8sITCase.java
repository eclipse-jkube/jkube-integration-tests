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
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistryHost;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
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
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.getImageHistory;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
import static org.eclipse.jkube.integrationtests.springboot.zeroconfig.ZeroConfig.MAVEN_APPLICATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@Application(MAVEN_APPLICATION)
@DockerRegistry
@TestMethodOrder(OrderAnnotation.class)
class ZeroConfigK8sITCase extends ZeroConfig implements MavenCase {

  @DockerRegistryHost
  private String registry;

  @Override
  public String getProject() {
    return MAVEN_PROJECT_ZERO_CONFIG;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create layered jar image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests", "spring-boot-zero-config");
    final List<String> imageFiles = listImageFiles("integration-tests/spring-boot-zero-config", "/deployments");
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/lib"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/classes"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/classpath.idx"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/layers.idx"));
    assertThat(imageFiles, hasItem("/deployments/org/springframework/boot/loader/JarLauncher.class"));
    final List<String> imageHistory = getImageHistory(String.format("%s/%s", "integration-tests", getApplication()));
    long dirCopyLayers = imageHistory.stream()
      .filter(l -> !l.startsWith("<missing>"))
      .filter(l -> l.contains("COPY dir:"))
      .count();
    assertThat(dirCopyLayers, equalTo(3L));
  }

  @Test
  @Order(2)
  @DisplayName("k8s:push, should push image to remote registry")
  void k8sPush() throws Exception {
    // Given
    final Properties properties = properties("jkube.docker.push.registry", registry);
    // When
    final InvocationResult invocationResult = maven("k8s:push", properties);
    // Then
    assertInvocation(invocationResult);
    assertThat(httpGet("http://" + registry + "/v2/integration-tests/spring-boot-zero-config/tags/list").body(),
      containsString("{\"name\":\"integration-tests/spring-boot-zero-config\",\"tags\":[\"latest\"]}"));
  }

  @Test
  @Order(3)
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
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/spring-boot-zero-config-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/spring-boot-zero-config-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(4)
  @DisplayName("k8s:helm, should create Helm charts")
  void k8sHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:helm");
    // Then
    assertInvocation(invocationResult);
    assertThat( new File(String.format("../%s/target/jkube/helm/%s/kubernetes/%s-0.0.0-SNAPSHOT.tar.gz", getProject(), getApplication(), getApplication()))
      .exists(), equalTo(true));
    final File helmDirectory = new File(
      String.format("../%s/target/jkube/helm/%s/kubernetes", getProject(), getApplication()));
    assertHelm(helmDirectory);
    assertThat(new File(helmDirectory, "templates/spring-boot-zero-config-deployment.yaml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(5)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service")
  @SuppressWarnings("unchecked")
  void k8sApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertInvocation(invocationResult);
    final Pod pod = assertThatShouldApplyResources();
    awaitDeployment(this, pod.getMetadata().getNamespace())
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", equalTo("integration-tests/spring-boot-zero-config:latest")),
        hasProperty("name", equalTo("spring-boot")),
        hasProperty("ports", hasSize(3)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("http")),
          hasProperty("containerPort", equalTo(8080))
        )))
      )));
  }

  @Test
  @Order(6)
  @DisplayName("k8s:log, should retrieve log")
  void k8sLog() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:log", properties("jkube.log.follow", "false"));
    // Then
    assertInvocation(invocationResult);
    assertThat(invocationResult.getStdOut(),
      stringContainsInOrder("Tomcat started on port(s): 8080", "Started ZeroConfigApplication in", "seconds"));
  }

  @Test
  @Order(7)
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

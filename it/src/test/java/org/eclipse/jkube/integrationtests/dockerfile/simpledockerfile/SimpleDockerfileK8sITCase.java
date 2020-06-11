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
package org.eclipse.jkube.integrationtests.dockerfile.simpledockerfile;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import org.eclipse.jkube.integrationtests.docker.RegistryExtension;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.assertDeploymentExists;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;

@Tag(KUBERNETES)
@ExtendWith(RegistryExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimpleDockerfileK8sITCase extends BaseMavenCase implements JKubeCase {

  private static final String SIMPLE_DOCKERFILE = "projects-to-be-tested/dockerfile/simple-dockerfile";

  private KubernetesClient k;

  @BeforeEach
  void setUp() {
    k = new DefaultKubernetesClient();
  }

  @Override
  public String getProject() {
    return SIMPLE_DOCKERFILE;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertImageWasRecentlyBuilt("integration-tests", "simple-dockerfile");
    final File targetDockerfile = new File(
      String.format("../%s/target/docker/integration-tests/" + getApplication() + "/latest/build/Dockerfile", getProject()));
    final File expectedDockerfile = new File(String.format("../%s/Dockerfile", getProject()));
    assertTrue(FileUtils.contentEquals(expectedDockerfile, targetDockerfile));
    final List<String> imageFiles = listImageFiles(String.format("%s/%s", "integration-tests", getApplication()),
      "/tmp");
    assertThat(imageFiles, hasItem("/tmp/my-file.txt"));
    assertThat(imageFiles, hasItem("/tmp/simple-dockerfile-0.0.0-SNAPSHOT.jar"));
  }

  @Test
  @Order(2)
  @DisplayName("k8s:push, should push image to remote registry")
  void k8sPush() throws Exception {
    // Given
    final Properties properties = new Properties();
    properties.setProperty("docker.push.registry", "localhost:5000");
    // When
    final InvocationResult invocationResult = maven("k8s:push", properties);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final Response response = new OkHttpClient.Builder().build().newCall(new Request.Builder()
      .get().url("http://localhost:5000/v2/integration-tests/simple-dockerfile/tags/list").build())
      .execute();
    assertNotNull(response.body());
    assertThat(response.body().string(),
      containsString("{\"name\":\"integration-tests/simple-dockerfile\",\"tags\":[\"latest\"]}"));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/simple-dockerfile-deployment.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(4)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service")
  @SuppressWarnings("unchecked")
  void k8sApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final Pod pod = assertThatShouldApplyResources();
    awaitDeployment(this, pod.getMetadata().getNamespace())
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", equalTo("integration-tests/simple-dockerfile:latest")),
        hasProperty("name", equalTo("integration-tests-simple-dockerfile")),
        hasProperty("ports", hasSize(1)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("http")),
          hasProperty("containerPort", equalTo(8080))
        )))
      )));
  }

  @Test
  @Order(5)
  @DisplayName("k8s:log, should retrieve log")
  void k8sLog() throws Exception {
    // Given
    final Properties properties = new Properties();
    properties.setProperty("jkube.log.follow", "false");
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final MavenUtils.InvocationRequestCustomizer irc = invocationRequest -> invocationRequest.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));
    // When
    final InvocationResult invocationResult = maven("k8s:log", properties, irc);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThat(baos.toString(StandardCharsets.UTF_8),
      stringContainsInOrder("Tomcat started on port(s): 8080", "Started Application in", "seconds"));
  }

  @Test
  @Order(6)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(this);
    assertDeploymentExists(this, equalTo(false));
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started Application in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertExposed()
      .assertIsNodePort()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", equalTo("Hello world!"));
    return pod;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return k;
  }

  @Override
  public String getApplication() {
    return "simple-dockerfile";
  }
}


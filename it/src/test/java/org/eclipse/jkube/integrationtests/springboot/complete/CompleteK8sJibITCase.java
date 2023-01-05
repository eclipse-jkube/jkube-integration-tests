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
package org.eclipse.jkube.integrationtests.springboot.complete;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistryHost;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.loadTar;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@DockerRegistry(port = 5005)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompleteK8sJibITCase extends Complete {

  private KubernetesClient k;

  @DockerRegistryHost
  private String registry;

  private Properties mvnProperties;

  @BeforeEach
  void setUp() {
    k = new KubernetesClientBuilder().build();
    mvnProperties = new Properties();
    mvnProperties.setProperty("jkube.generator.name", registry + "/sb/sb-complete");
  }

  @AfterEach
  void tearDown() {
    k.close();
    k = null;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return k;
  }

  @Override
  protected List<String> getProfiles() {
    return Collections.singletonList("JIB");
  }

  @Override
  public String getApplication() {
    return "spring-boot-complete-jib";
  }

  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource", mvnProperties);
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube-jib/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube-jib/kubernetes/spring-boot-complete-jib-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube-jib/kubernetes/spring-boot-complete-jib-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create JIB image")
  void k8sBuild() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:build", mvnProperties);
    // Then
    assertInvocation(invocationResult);
    assertThat(invocationResult.getStdOut(), stringContainsInOrder(
      "JIB image build started",
      "JIB> [==============================] 100.0% complete",
      "docker-build.tar successfully built"
    ));
  }

  @Test
  @Order(2)
  @DisplayName("k8s:push, should push image to remote registry")
  void k8sPush() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:push", mvnProperties);
    // Then
    assertInvocation(invocationResult);
    assertThat(invocationResult.getStdOut(), containsString("JIB> [==============================] 100.0% complete"));
    try (Response response = new OkHttpClient.Builder().build().newCall(new Request.Builder()
      .get().url("http://" + registry + "/v2/sb/sb-complete/tags/list").build()).execute()) {
      assertThat(response.body().string(),
        containsString("{\"name\":\"sb/sb-complete\",\"tags\":[\"latest\"]}"));
    }
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service")
  void k8sApply() throws Exception {
    // Given
    // No easy way to share a local registry within Minikube and test runner host (for every environment)
    loadTar(new File(String.format(
      "../%s/target/docker/localhost/5005/sb/sb-complete/tmp/docker-build.tar", getProject())));
    // When
    final InvocationResult invocationResult = maven("k8s:apply", mvnProperties);
    // Then
    assertInvocation(invocationResult);
    assertThatShouldApplyResources()
      .assertNodePortResponse("us-cli", containsString("hello"), "jkube", "hello");
  }

  @Test
  @Order(4)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy", mvnProperties);
    // Then
    assertInvocation(invocationResult);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }
}

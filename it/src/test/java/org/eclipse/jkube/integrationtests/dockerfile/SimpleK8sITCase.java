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
package org.eclipse.jkube.integrationtests.dockerfile;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;
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
import java.util.List;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimpleK8sITCase extends BaseMavenCase implements JKubeCase {

  private static final String PROJECT_SIMPLE = "projects-to-be-tested/maven/dockerfile/simple";

  private KubernetesClient k;

  @BeforeEach
  void setUp() {
    k = new DefaultKubernetesClient();
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
  public String getApplication() {
    return "dockerfile-simple";
  }

  @Override
  public String getProject() {
    return PROJECT_SIMPLE;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests", getApplication());
    final File targetDockerfile = new File(
      String.format("../%s/target/docker/integration-tests/%s/latest/build/Dockerfile", getProject(), getApplication()));
    final File expectedDockerfile = new File(String.format("../%s/Dockerfile", getProject()));
    assertTrue(FileUtils.contentEquals(expectedDockerfile, targetDockerfile));
    final List<String> imageFiles = listImageFiles(String.format("%s/%s", "integration-tests", getApplication()),
      "/deployments");
    assertThat(imageFiles, hasItem("/deployments/my-file.txt"));
    assertThat(imageFiles, hasItem("/deployments/app.jar"));
  }


  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifests")
  @SuppressWarnings("unchecked")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/dockerfile-simple-deployment.yml"), yaml(allOf(
      not(anEmptyMap()),
      hasEntry(is("spec"), hasEntry(is("template"), hasEntry(is("spec"), hasEntry(is("containers"),
        containsInAnyOrder(hasEntry(is("ports"), containsInAnyOrder(hasEntry("containerPort", 8080))))
      ))))
    )));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/dockerfile-simple-service.yml"), yaml(allOf(
      not(anEmptyMap()),
      hasEntry(is("spec"), hasEntry("type", "NodePort"))
    )));
  }

  @Test
  @Order(2)
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
        hasProperty("image", equalTo("integration-tests/dockerfile-simple:latest")),
        hasProperty("name", equalTo("integration-tests-dockerfile-simple")),
        hasProperty("ports", hasSize(1)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("http")),
          hasProperty("containerPort", equalTo(8080))
        )))
      )));
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

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started Application in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", equalTo("Hello world!"))
      .assertNodePortResponse("http",
        containsString("This is a dummy file which should be copied inside Dockerfile"), "static-file");
    return pod;
  }

}


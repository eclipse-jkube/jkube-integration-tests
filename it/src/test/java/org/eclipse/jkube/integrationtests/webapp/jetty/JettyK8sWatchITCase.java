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
package org.eclipse.jkube.integrationtests.webapp.jetty;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@KubernetesTest(createEphemeralNamespace = false)
class JettyK8sWatchITCase implements JKubeCase, MavenCase {

  private static final String PROJECT_JETTY_WATCH = "projects-to-be-tested/maven/webapp/jetty-watch";

  private static KubernetesClient kubernetesClient;
  private File fileToChange;
  private String originalFileContent;
  private Pod originalPod;
  private Future<MavenInvocationResult> mavenWatch;

  @BeforeEach
  void setUp() throws Exception {
    fileToChange = new File(String.format("../%s/src/main/webapp/index.html", getProject()));
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    // Tests start with a fresh deployment to watch for
    assertInvocation(maven("clean package k8s:build k8s:resource k8s:apply"));
    originalPod = assertThatShouldApplyResources("<h2>Eclipse JKube on Jetty rocks!</h2>");
  }

  @AfterEach
  void tearDown() throws IOException, MavenInvocationException, InterruptedException {
    if (mavenWatch != null) {
      mavenWatch.cancel(true);
    }
    kubernetesClient.resource(originalPod).withGracePeriod(0).delete();
    assertInvocation(maven("k8s:undeploy"));
    FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
  }

  @Override
  public String getProject() {
    return PROJECT_JETTY_WATCH;
  }

  @Override
  public String getApplication() {
    return "webapp-jetty-watch";
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Test
  @DisplayName("k8s:watch, with mode=both, SHOULD hot deploy the application")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchBuildAndRun() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync(
        "k8s:watch", properties("jkube.watch.mode", "both"), baos, null);
      await(baos::toString).apply(log -> log.contains("Waiting ...")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, "<html><body><h2>Eclipse JKube Jetty v2</h2></body></html>", StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
      await(baos::toString).apply(log -> log.contains("Updating Deployment")).get(10, TimeUnit.SECONDS);
      // Then
      kubernetesClient.pods().resource(originalPod).waitUntilCondition(Objects::isNull, 30, TimeUnit.SECONDS);
      assertThat(baos.toString(StandardCharsets.UTF_8), stringContainsInOrder(
        "Waiting ...",
        // Build
        "Built image sha256:",
        // Run
        "Updating Deployment " + getApplication() + " to use image: integration-tests/" + getApplication() + ":snapshot"
      ));
      assertThatShouldApplyResources("<h2>Eclipse JKube Jetty v2</h2>");
    }
  }

  @Test
  @DisplayName("k8s:watch, with mode=none, SHOULD NOT hot deploy the application")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchNone() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync(
        "k8s:watch", properties("jkube.watch.mode", "none"), baos, null);
      await(baos::toString).apply(log -> log.contains("Waiting ...")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, "<html><body><h2>Eclipse JKube Jetty v2</h2></body></html>", StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
      // Then
      assertThat(baos.toString(StandardCharsets.UTF_8), endsWith("Waiting ...\n"));
      assertThatShouldApplyResources("<h2>Eclipse JKube on Jetty rocks!</h2>");
    }
  }

  @Test
  @DisplayName("k8s:watch, with mode=copy, SHOULD hot deploy the application")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchCopy() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync(
        "k8s:watch", properties("jkube.watch.mode", "copy"), baos, null);
      await(baos::toString).apply(log -> log.contains("Waiting ...")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, "<html><body><h2>Eclipse JKube Jetty v2</h2></body></html>", StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
      // Then
      await(baos::toString)
        .apply(log -> log.contains("Files successfully copied to the container."))
        .get(10, TimeUnit.SECONDS);
      waitUntilApplicationRestartsInsidePod();
      assertThatShouldApplyResources("<h2>Eclipse JKube Jetty v2</h2>");
    }
  }

  private Pod assertThatShouldApplyResources(String response) throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Server:main: Started", 120);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", containsString(response));
    return pod;
  }

  private void waitUntilApplicationRestartsInsidePod() throws ExecutionException, InterruptedException, TimeoutException {
    PodResource podResource = getKubernetesClient().pods().resource(originalPod);
    await(podResource::getLog)
      .apply(l -> l.contains("Stopped o.e.j.w.WebAppContext"))
      .get(10, TimeUnit.SECONDS);
    await(podResource::getLog)
      .apply(l -> l.contains("ContextHandler:Scanner-0: Started o.e.j.w.WebAppContext"))
      .get(10, TimeUnit.SECONDS);
  }
}

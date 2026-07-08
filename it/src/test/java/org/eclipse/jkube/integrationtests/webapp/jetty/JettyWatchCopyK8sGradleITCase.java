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
package org.eclipse.jkube.integrationtests.webapp.jetty;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.commons.io.FileUtils;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@Application("webapp-jetty-watch-copy")
@KubernetesTest(createEphemeralNamespace = false)
class JettyWatchCopyK8sGradleITCase implements JKubeCase {

  @Gradle(project = "webapp-jetty-watch-copy")
  private JKubeGradleRunner gradle;

  private KubernetesClient kubernetesClient;
  private File fileToChange;
  private String originalFileContent;
  private Pod originalPod;
  private CompletableFuture<BuildResult> gradleWatch;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @BeforeEach
  void setUp() throws Exception {
    fileToChange = gradle.getModulePath().resolve("src").resolve("main")
      .resolve("webapp").resolve("index.html").toFile();
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    gradle.tasks(false, true, "k8sBuild", "k8sResource", "k8sApply").build();
    originalPod = assertThatShouldApplyResources("<h2>Eclipse JKube on Jetty rocks!</h2>");
  }

  @AfterEach
  void tearDown() throws Exception {
    if (gradleWatch != null) {
      gradleWatch.cancel(true);
    }
    kubernetesClient.resource(originalPod).withGracePeriod(0).delete();
    gradle.tasks(false, true, "k8sUndeploy").build();
    FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("k8sWatch, with mode=copy, SHOULD hot deploy the application (Gradle)")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchCopy() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      gradleWatch = gradle.tasksAsync(baos, false, true, "k8sWatch");
      await(baos::toString).apply(log -> log.contains("Waiting ...")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, "<html><body><h2>Eclipse JKube Jetty v2</h2></body></html>", StandardCharsets.UTF_8);
      gradle.tasks(false, true, "war").build();
      // Then
      try {
        await(baos::toString)
          .apply(log -> log.contains("Files successfully copied to the container."))
          .get(30, TimeUnit.SECONDS);
      } catch (TimeoutException ex) {
        throw new AssertionError("Expected message containing: 'Files successfully copied to the container.' but got: \n\n" + baos, ex);
      }
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
      .apply(l -> l.contains("Stopped oeje10w.WebAppContext"))
      .get(10, TimeUnit.SECONDS);
    await(podResource::getLog)
      .apply(l -> l.indexOf("Started oeje10w.WebAppContext", l.indexOf("Stopped oeje10w.WebAppContext")) > 0)
      .get(10, TimeUnit.SECONDS);
  }
}

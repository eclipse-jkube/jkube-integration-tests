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
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.apache.commons.io.FileUtils;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@Application(JettyK8sWatch.GRADLE_APPLICATION)
@KubernetesTest(createEphemeralNamespace = false)
class JettyWatchCopyK8sGradleITCase extends JettyK8sWatch {

  @Gradle(project = "wa-jetty-wc")
  private JKubeGradleRunner gradle;

  private CompletableFuture<BuildResult> gradleWatch;

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
    try {
      if (gradleWatch != null) {
        gradleWatch.cancel(true);
      }
      if (originalPod != null) {
        kubernetesClient.resource(originalPod).withGracePeriod(0).delete();
      }
      gradle.tasks(false, true, "k8sUndeploy").build();
    } finally {
      FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("k8sWatch, with mode=copy, SHOULD hot deploy the application (Gradle)")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchCopy() throws Exception {
    // Verify scanInterval=1 reaches the deployed pod (jkube#3928)
    assertScanIntervalReachesDeployedPod();
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

  private void assertScanIntervalReachesDeployedPod() throws Exception {
    final String namespace = originalPod.getMetadata().getNamespace();
    final var output = new ByteArrayOutputStream();
    try (ExecWatch ignored = kubernetesClient.pods().inNamespace(namespace)
        .withName(originalPod.getMetadata().getName())
        .writingOutput(output).writingError(output)
        .exec("env")) {
      ignored.exitCode().get(30, TimeUnit.SECONDS);
    }
    assertThat(output.toString(StandardCharsets.UTF_8),
      containsString("jetty.deploy.scanInterval=1"));
  }
}

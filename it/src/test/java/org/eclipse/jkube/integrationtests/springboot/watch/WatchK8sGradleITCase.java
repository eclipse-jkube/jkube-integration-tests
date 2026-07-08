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
package org.eclipse.jkube.integrationtests.springboot.watch;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.FileUtils;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@Application("sb-watch")
@KubernetesTest(createEphemeralNamespace = false)
@Disabled("Spring Boot DevTools remote upload fails on Minikube networking")
public class WatchK8sGradleITCase implements JKubeCase {

  @Gradle(project = "sb-watch")
  private JKubeGradleRunner gradle;

  private static KubernetesClient kubernetesClient;
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
    fileToChange = gradle.getModulePath()
      .resolve("src").resolve("main").resolve("java")
      .resolve("org").resolve("eclipse").resolve("jkube").resolve("integrationtests")
      .resolve("springbootwatch").resolve("SpringBootWatchResource.java").toFile();
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    gradle.tasks(false, true, "k8sBuild", "k8sResource", "k8sApply").build();
    originalPod = assertThatShouldApplyResources("Spring Boot Watch v1");
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
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8sWatch, SHOULD hot reload application on changes (Gradle)")
  void watch_whenSourceModified_shouldLiveReloadChanges() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      gradleWatch = gradle.tasksAsync(baos, false, true, "k8sWatch");
      await(baos::toString).apply(log -> log.contains("Started RemoteSpringApplication")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, originalFileContent.replace(
        "\"Spring Boot Watch v1\";", "\"Spring Boot Watch v2\";"), StandardCharsets.UTF_8);
      gradle.tasks(false, true, "build").build();
      try {
        await(baos::toString).apply(log -> log.contains("Remote server has changed, triggering LiveReload"))
          .get(1, TimeUnit.MINUTES);
      } catch (TimeoutException ex) {
        throw new AssertionError("LiveReload not triggered, check the watch output for details:\n" + baos);
      }
      // Then
      assertThat(baos.toString(StandardCharsets.UTF_8), stringContainsInOrder(
        "Running watcher spring-boot",
        ":: Spring Boot Remote ::",
        "LiveReload server is running on port",
        "Remote server has changed, triggering LiveReload"
      ));
      awaitPod(this).logContains("restartedMain]", 60);
      assertThatShouldApplyResources("Spring Boot Watch v2");
    }
  }

  private Pod assertThatShouldApplyResources(String expectedMessage) throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started SpringBootWatchApplication in", 120)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", equalTo(expectedMessage));
    return pod;
  }
}

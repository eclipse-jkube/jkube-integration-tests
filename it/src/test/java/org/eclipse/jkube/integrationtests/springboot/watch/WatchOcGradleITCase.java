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
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@Application(Watch.GRADLE_APPLICATION)
@KubernetesTest(createEphemeralNamespace = false)
@Isolated
public class WatchOcGradleITCase extends Watch {

  @Gradle(project = "sb-watch")
  private JKubeGradleRunner gradle;

  private CompletableFuture<BuildResult> gradleWatch;

  @BeforeEach
  void setUp() throws Exception {
    fileToChange = gradle.getModulePath()
      .resolve("src").resolve("main").resolve("java")
      .resolve("org").resolve("eclipse").resolve("jkube").resolve("integrationtests")
      .resolve("springbootwatch").resolve("SpringBootWatchResource.java").toFile();
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    gradle.tasks(false, true, "ocBuild", "ocResource", "ocApply").build();
    originalPod = assertThatShouldApplyResources("Spring Boot Watch v1");
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
      gradle.tasks(false, true, "ocUndeploy").build();
    } finally {
      FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
    }
  }

  @Test
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("ocWatch, SHOULD hot reload application on changes (Gradle)")
  void watch_whenSourceModified_shouldLiveReloadChanges() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      gradleWatch = gradle.tasksAsync(baos, false, true, "ocWatch");
      await(baos::toString).apply(log -> log.contains(":: Spring Boot Remote ::")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, originalFileContent.replace(
        "\"Spring Boot Watch v1\";", "\"Spring Boot Watch v2\";"), StandardCharsets.UTF_8);
      gradle.tasks(false, true, "build").build();
      // Then — SLF4J log assertions (LiveReload, Started) unavailable in Gradle (jkube#3960)
      assertThat(baos.toString(StandardCharsets.UTF_8), stringContainsInOrder(
        "Running watcher spring-boot",
        ":: Spring Boot Remote ::"
      ));
      awaitPod(this).logContains("restartedMain]", 120);
      assertThatShouldApplyResources("Spring Boot Watch v2");
    }
  }
}

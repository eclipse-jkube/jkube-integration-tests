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
package org.eclipse.jkube.integrationtests.springboot.watch;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import org.apache.commons.io.FileUtils;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@KubernetesTest(createEphemeralNamespace = false)
abstract class WatchMaven extends Watch implements MavenCase {

  private static final String PROJECT_SPRING_BOOT_WATCH = "projects-to-be-tested/maven/spring/watch";

  private Future<MavenInvocationResult> mavenWatch;

  abstract String getPrefix();

  @Override
  public String getProject() {
    return PROJECT_SPRING_BOOT_WATCH;
  }

  @Override
  public String getApplication() {
    return MAVEN_APPLICATION;
  }

  @BeforeEach
  void setUp() throws Exception {
    fileToChange = new File(String.format(
      "../%s/src/main/java/org/eclipse/jkube/integrationtests/springbootwatch/SpringBootWatchResource.java", getProject()));
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    assertInvocation(maven(String.format("clean package %1$s:build %1$s:resource %1$s:apply", getPrefix())));
    originalPod = assertThatShouldApplyResources("Spring Boot Watch v1");
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      if (mavenWatch != null) {
        mavenWatch.cancel(true);
      }
      if (originalPod != null) {
        kubernetesClient.resource(originalPod).withGracePeriod(0).delete();
      }
      assertInvocation(maven(String.format("%s:undeploy", getPrefix())));
    } finally {
      FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
    }
  }

  @Test
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("watch, SHOULD hot reload application on changes")
  void watch_whenSourceModified_shouldLiveReloadChanges() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync(String.format("%s:watch", getPrefix()), null, baos, null);
      await(baos::toString).apply(log -> log.contains("Started RemoteSpringApplication")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, originalFileContent.replace(
        "\"Spring Boot Watch v1\";", "\"Spring Boot Watch v2\";"), StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
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
        "Started RemoteSpringApplication",
        "Remote server has changed, triggering LiveReload"
      ));
      awaitPod(this).logContains("restartedMain]", 60);
      assertThatShouldApplyResources("Spring Boot Watch v2");
    }
  }
}

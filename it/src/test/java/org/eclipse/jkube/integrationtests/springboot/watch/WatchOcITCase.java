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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.io.FileUtils;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.WaitUtil.await;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
class WatchOcITCase extends Watch {
  private KubernetesClient k;
  private File fileToChange;
  private String originalFileContent;
  private Pod originalPod;
  private Future<MavenInvocationResult> mavenWatch;

  @BeforeEach
  void setUp() throws Exception {
    k = new KubernetesClientBuilder().build();
    fileToChange = new File(String.format(
      "../%s/src/main/java/org/eclipse/jkube/integrationtests/springbootwatch/SpringBootWatchResource.java", getProject()));
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    // Tests start with a fresh deployment to watch for
    assertInvocation(maven("clean package oc:build oc:resource oc:apply"));
    originalPod = assertThatShouldApplyResources("Spring Boot Watch v1");
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mavenWatch != null) {
      mavenWatch.cancel(true);
    }
    k.resource(originalPod).withGracePeriod(0).delete();
    assertInvocation(maven("oc:undeploy"));
    FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
    k.close();
    k = null;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return k;
  }

  @Test
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:watch, SHOULD hot reload application on changes")
  void ocWatch_whenSourceModified_shouldLiveReloadChanges() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync(
        "oc:watch", null, baos, null);
      await(baos::toString).apply(log -> log.contains("Started RemoteSpringApplication")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, originalFileContent.replace(
        "\"Spring Boot Watch v1\";", "\"Spring Boot Watch v2\";"), StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
      await(baos::toString).apply(log -> log.contains("Remote server has changed, triggering LiveReload"))
        .get(2, TimeUnit.MINUTES);
      // Then
      assertThat(baos.toString(StandardCharsets.UTF_8), stringContainsInOrder(
        "Running watcher spring-boot",
        ":: Spring Boot Remote ::",
        "LiveReload server is running on port",
        "Remote server has changed, triggering LiveReload"
      ));
      awaitPod(this).logContains("restartedMain]", 10);
      assertThatShouldApplyResources("Spring Boot Watch v2");
    }
  }
}


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
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;
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

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.WaitUtil.await;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

abstract class Watch  extends BaseMavenCase implements JKubeCase {

  private static final String PROJECT_SPRING_BOOT_WATCH = "projects-to-be-tested/maven/spring/watch";

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
    assertInvocation(maven(String.format("clean package %1$s:build %1$s:resource %1$s:apply", getPrefix())));
    originalPod = assertThatShouldApplyResources("Spring Boot Watch v1");
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mavenWatch != null) {
      mavenWatch.cancel(true);
    }
    k.resource(originalPod).withGracePeriod(0).delete();
    assertInvocation(maven(String.format("%s:undeploy", getPrefix())));
    FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
    k.close();
    k = null;
  }

  abstract String getPrefix();

  @Override
  public KubernetesClient getKubernetesClient() {
    return k;
  }

  @Override
  public String getProject() {
    return PROJECT_SPRING_BOOT_WATCH;
  }

  @Override
  public String getApplication() {
    return "spring-boot-watch";
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
          .get(2, TimeUnit.MINUTES);
      } catch (TimeoutException ex) {
        // If this test is not run in an isolated Minikube environment, it might fail due to:
        // o.s.b.d.r.c.ClassPathChangeUploader      : A failure occurred when uploading to http://localhost:51337/.~~spring-boot!~/restart. Upload will be retried in 2 seconds
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

  final Pod assertThatShouldApplyResources(String expectedMessage) throws Exception {
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

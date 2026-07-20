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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.FileUtils;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@Application("sb-watch")
@KubernetesTest(createEphemeralNamespace = false)
@Isolated
@Disabled("Gradle DevTools watcher hardcodes Maven target/classes path (jkube#3947)")
public class WatchOcGradleITCase implements JKubeCase {

  @Gradle(project = "sb-watch")
  private JKubeGradleRunner gradle;

  private static KubernetesClient kubernetesClient;
  private File fileToChange;
  private String originalFileContent;
  private Pod originalPod;

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
    gradle.tasks(false, true, "ocBuild", "ocResource", "ocApply").build();
    originalPod = assertThatShouldApplyResources("Spring Boot Watch v1");
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
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
    // Blocked by jkube#3947: Gradle DevTools watcher hardcodes Maven's target/classes path.
    // Will be implemented with tasksAsync("ocWatch") once jkube#3947 is resolved and
    // JKubeGradleRunner.tasksAsync() is available (PR #415).
    throw new UnsupportedOperationException("Blocked by jkube#3947");
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

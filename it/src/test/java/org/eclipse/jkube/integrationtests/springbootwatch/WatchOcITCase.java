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
package org.eclipse.jkube.integrationtests.springbootwatch;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.WaitUtil.await;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
class WatchOcITCase extends Watch {
  private KubernetesClient k;

  @BeforeEach
  void setUp() {
    k = new KubernetesClientBuilder().build();
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

  @Test
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:watch, should hot reload application on changes")
  void ocWatch_whenSourceModified_shouldLiveReloadChanges() throws Exception {
    MavenInvocationResult buildResourceApplyResult = maven("clean package oc:build oc:resource oc:apply");
    assertInvocation(buildResourceApplyResult);
    File f = new File(String.format("../%s/src/main/java/org/eclipse/jkube/integrationtests/springbootwatch/SpringBootWatchResource.java", getProject()));
    String oldContent = IOUtils.toString(new FileReader(f));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<MavenInvocationResult> watchMavenInvocationResultFuture = null;
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
         final PrintStream printStream = new PrintStream(baos)) {
      // Given
      watchMavenInvocationResultFuture = executor.submit(() -> {
        try {
          return maven("oc:watch", new Properties(), i -> i.setOutputHandler(new PrintStreamHandler(printStream, true)));
        } catch (IOException | MavenInvocationException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      });
      await(baos::toString).apply(log -> log.contains("Started RemoteSpringApplication")).get(2, TimeUnit.MINUTES);

      // When
      assertThatShouldApplyResources("Spring Boot Watch v1");

      String newContent = oldContent.replace("\"Spring Boot Watch v1\";", "\"Spring Boot Watch v2\";");
      Files.write(f.toPath(), newContent.getBytes());
      final MavenInvocationResult packageInvocationResult = maven("package");
      assertInvocation(packageInvocationResult);
      await(baos::toString).apply(log -> log.contains("triggering LiveReload")).get(2, TimeUnit.MINUTES);
      await(baos::toString).apply(log -> log.contains("Uploaded")).get(30, TimeUnit.SECONDS);
      await(baos::toString).apply(log -> log.contains("Completed initialization")).get(30, TimeUnit.SECONDS);

      // Then
      assertThatShouldApplyResources("Spring Boot Watch v2");
    } finally {
      Files.write(f.toPath(), oldContent.getBytes());
      if (watchMavenInvocationResultFuture != null) {
        watchMavenInvocationResultFuture.cancel(true);
      }
      MavenInvocationResult undeployResult = maven("oc:undeploy");
      assertInvocation(undeployResult);
      assertJKube(this)
        .assertThatShouldDeleteAllAppliedResources();
      assertJKube(this)
        .assertDeploymentDeleted();
    }
  }
}


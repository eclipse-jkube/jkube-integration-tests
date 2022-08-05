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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.commons.io.IOUtils;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.WaitUtil.waitUntilCondition;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
class JettyK8sWatchITCase extends Jetty {
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

  private static Stream<Arguments> getWatchModeAndExpectedResponse() {
    return Stream.of(
      Arguments.of("both", "<h2>Eclipse JKube on Jetty rocks!</h2>", "<h2>Eclipse JKube Jetty v2</h2>", "Updating Deployment", true, false),
      Arguments.of("none", "<h2>Eclipse JKube on Jetty rocks!</h2>", "<h2>Eclipse JKube on Jetty rocks!</h2>", "", false, false)
    );
  }

  @ParameterizedTest(name = "{index} k8s:watch -Djkube.watch.mode={0}")
  @MethodSource("getWatchModeAndExpectedResponse")
  @DisplayName("k8s:watch, in provided mode, should hot deploy application")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatch(String watchMode, String firstDeploymentResponse, String secondDeploymentResponse, String redeployLog, boolean waitForRedeployment, boolean waitUntilAppRestarts) throws Exception {
    MavenInvocationResult buildResourceApplyResult = maven("clean package k8s:build k8s:resource k8s:apply");
    assertInvocation(buildResourceApplyResult);
    File f = new File(String.format("../%s/src/main/webapp/index.html", getProject()));
    String oldContent = IOUtils.toString(new FileReader(f));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Properties watchProperties = new Properties();
    watchProperties.put("jkube.watch.mode", watchMode);
    Future<MavenInvocationResult> watchMavenInvocationResultFuture = null;
    Pod currentDeployedPod = null;
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
         final PrintStream printStream = new PrintStream(baos)) {
      // Given
      watchMavenInvocationResultFuture = executor.submit(() -> {
        try {
          return maven("k8s:watch", watchProperties, i -> i.setOutputHandler(new PrintStreamHandler(printStream, true)));
        } catch (IOException | MavenInvocationException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      });
      waitUntilCondition(baos, b -> b.toString().contains("Waiting ..."), TimeUnit.MINUTES, 2);

      // When
      currentDeployedPod = assertThatShouldApplyResources(firstDeploymentResponse);
      Files.write(f.toPath(), "<body><h2>Eclipse JKube Jetty v2</h2></body>".getBytes());
      final MavenInvocationResult packageInvocationResult = maven("package");
      waitUntilCondition(baos, b -> b.toString().contains(redeployLog), TimeUnit.SECONDS, 10);

      // Then
      assertInvocation(packageInvocationResult);
      waitUntilOldPodIsDeleted(waitForRedeployment, currentDeployedPod);
      waitUntilApplicationRestartsInsidePod(waitUntilAppRestarts, currentDeployedPod);
      currentDeployedPod = assertThatShouldApplyResources(secondDeploymentResponse);
    } finally {
      Files.write(f.toPath(), oldContent.getBytes());
      if (watchMavenInvocationResultFuture != null) {
        watchMavenInvocationResultFuture.cancel(true);
      }
      MavenInvocationResult undeployResult = maven("k8s:undeploy");
      assertInvocation(undeployResult);
      assertJKube(this)
        .assertThatShouldDeleteAllAppliedResources();
      assertJKube(this)
        .assertDeploymentDeleted();
      if (currentDeployedPod != null) {
        getKubernetesClient().pods().inNamespace(currentDeployedPod.getMetadata().getNamespace())
          .withName(currentDeployedPod.getMetadata().getName())
          .waitUntilCondition(Objects::isNull, 2, TimeUnit.MINUTES);
      }
    }
  }

  private void waitUntilOldPodIsDeleted(boolean waitForRedeployment, Pod currentDeployedPod) {
    if (waitForRedeployment) {
      getKubernetesClient().pods().inNamespace(currentDeployedPod.getMetadata().getNamespace())
        .withName(currentDeployedPod.getMetadata().getName())
        .waitUntilCondition(Objects::isNull, 2, TimeUnit.MINUTES);
    }
  }

  private void waitUntilApplicationRestartsInsidePod(boolean waitUntilAppRestart, Pod currentDeployedPod) throws InterruptedException, TimeoutException {
    if (waitUntilAppRestart) {
      PodResource podResource = getKubernetesClient().pods().resource(currentDeployedPod);
      waitUntilCondition(podResource, p -> p.getLog().contains("Stopped o.e.j.w.WebAppContext"), TimeUnit.SECONDS, 60);
      waitUntilCondition(podResource, p -> p.getLog().contains("ContextHandler:Scanner-0: Started o.e.j.w.WebAppContext"), TimeUnit.SECONDS, 10);
    }
  }
}

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

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

@KubernetesTest(createEphemeralNamespace = false)
abstract class JettyK8sWatch implements JKubeCase, MavenCase {

  static final String PROJECT_JETTY_WATCH = "projects-to-be-tested/maven/webapp/jetty-watch";
  static final String APPLICATION_JETTY_WATCH = "webapp-jetty-watch";

  private KubernetesClient kubernetesClient;
  File fileToChange;
  private String originalFileContent;
  Pod originalPod;
  Future<MavenInvocationResult> mavenWatch;

  @BeforeEach
  void setUp() throws Exception {
    fileToChange = new File(String.format("../%s/src/main/webapp/index.html", getProject()));
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    // Tests start with a fresh deployment to watch for
    assertInvocation(maven("clean package k8s:build k8s:resource k8s:apply"));
    originalPod = assertThatShouldApplyResources("<h2>Eclipse JKube on Jetty rocks!</h2>");
  }

  @AfterEach
  void tearDown() throws IOException, MavenInvocationException, InterruptedException {
    if (mavenWatch != null) {
      mavenWatch.cancel(true);
    }
    kubernetesClient.resource(originalPod).withGracePeriod(0).delete();
    assertInvocation(maven("k8s:undeploy"));
    FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  Pod assertThatShouldApplyResources(String response) throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Server:main: Started", 120);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", containsString(response));
    return pod;
  }

  void waitUntilApplicationRestartsInsidePod() throws ExecutionException, InterruptedException, TimeoutException {
    PodResource podResource = getKubernetesClient().pods().resource(originalPod);
    await(podResource::getLog)
      .apply(l -> l.contains("Stopped o.e.j.w.WebAppContext"))
      .get(10, TimeUnit.SECONDS);
    await(podResource::getLog)
      .apply(l -> l.contains("ContextHandler:Scanner-0: Started o.e.j.w.WebAppContext"))
      .get(10, TimeUnit.SECONDS);
  }
}

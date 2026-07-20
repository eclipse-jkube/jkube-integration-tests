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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

abstract class JettyK8sWatch implements JKubeCase {

  static final String PROJECT_JETTY_WATCH = "projects-to-be-tested/maven/webapp/jetty-watch";
  static final String APPLICATION_JETTY_WATCH = "webapp-jetty-watch";
  static final String GRADLE_APPLICATION = "wa-jetty-wc";

  KubernetesClient kubernetesClient;
  File fileToChange;
  String originalFileContent;
  Pod originalPod;

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
      .apply(l -> l.contains("Stopped oeje10w.WebAppContext"))
      .get(10, TimeUnit.SECONDS);
    await(podResource::getLog)
      .apply(l -> l.indexOf("Started oeje10w.WebAppContext", l.indexOf("Stopped oeje10w.WebAppContext")) > 0)
      .get(10, TimeUnit.SECONDS);
  }
}

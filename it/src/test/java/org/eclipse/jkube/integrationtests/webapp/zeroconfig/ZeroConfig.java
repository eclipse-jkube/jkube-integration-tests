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
package org.eclipse.jkube.integrationtests.webapp.zeroconfig;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;

abstract class ZeroConfig extends BaseMavenCase implements JKubeCase {

  static final String PROJECT_ZERO_CONFIG = "projects-to-be-tested/webapp/zero-config";

  @Override
  public String getProject() {
    return PROJECT_ZERO_CONFIG;
  }

  @Override
  public String getApplication() {
    return "webapp-zero-config";
  }

  final Pod assertThatShouldApplyResources() throws InterruptedException, IOException {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Catalina.start Server startup", 60);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, false);
    return pod;
  }

  @SuppressWarnings("squid:S2925")
  final Service serviceSpecTypeToNodePort() throws InterruptedException, IOException {
    final Pod pod = awaitPod(this).getKubernetesResource();
    final Service serviceToUpdate = getKubernetesClient().services()
      .inNamespace(pod.getMetadata().getNamespace())
      .withName(getApplication())
      .edit().editSpec().withType("NodePort").endSpec().done();
    final Service updatedService = getKubernetesClient().services()
      .inNamespace(serviceToUpdate.getMetadata().getNamespace())
      .withName(serviceToUpdate.getMetadata().getName())
      .waitUntilCondition(s -> s.getSpec().getType().equals("NodePort"), 10, TimeUnit.SECONDS);
    final long throttleMilliseconds = 500L;
    Thread.sleep(throttleMilliseconds);
    return updatedService;
  }

  final void assertLog(String log) {
    assertThat(log,
      stringContainsInOrder(
        "Deploying web application archive [/usr/local/tomcat/webapps/ROOT.war]",
        "Deployment of web application archive [/usr/local/tomcat/webapps/ROOT.war] has finished",
        "org.apache.catalina.startup.Catalina.start Server startup in", "seconds"));
  }
}

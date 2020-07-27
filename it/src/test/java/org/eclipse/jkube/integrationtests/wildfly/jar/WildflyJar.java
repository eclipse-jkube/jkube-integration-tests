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
package org.eclipse.jkube.integrationtests.wildfly.jar;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import java.io.IOException;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

abstract class WildflyJar extends BaseMavenCase implements JKubeCase {

  static final String PROJECT_WILDFLY_JAR = "projects-to-be-tested/wildfly-jar/microprofile";

  @Override
  public String getProject() {
    return PROJECT_WILDFLY_JAR;
  }

  @Override
  public String getApplication() {
    return "wildfly-jar-microprofile";
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this)
      .logContains("wildfly-jar-microprofile-0.0.0-SNAPSHOT-wildfly.jar", 15)
      .logContains("Deployed \"wildfly-jar-microprofile-0.0.0-SNAPSHOT.war\"", 60)
      .logContains("WFLYSRV0025", 15);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", equalTo("JKube from WildFly JAR rocks!"));
    return pod;
  }

}

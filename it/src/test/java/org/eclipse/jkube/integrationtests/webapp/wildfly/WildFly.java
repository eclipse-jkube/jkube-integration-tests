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
package org.eclipse.jkube.integrationtests.webapp.wildfly;


import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

abstract class WildFly extends BaseMavenCase implements JKubeCase {
  static final String PROJECT_WILDFLY="projects-to-be-tested/maven/webapp/wildfly";

  @Override
  public String getProject() {
    return PROJECT_WILDFLY;
  }

  @Override
  public String getApplication() {
    return "webapp-wildfly";
  }

  final Pod assertThatShouldApplyResources()  throws  Exception{
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Deployed", 60);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", containsString("<h2>Eclipse JKube rocks!!</h2>"));
    return pod;
  }
}

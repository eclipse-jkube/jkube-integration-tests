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
package org.eclipse.jkube.integrationtests.quarkus.rest;

import io.fabric8.kubernetes.api.model.Pod;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;

import java.io.File;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

abstract class Quarkus extends BaseMavenCase implements JKubeCase {

  private static final String PROJECT_QUARKUS_REST = "projects-to-be-tested/quarkus/rest";

  @Override
  public String getProject() {
    return PROJECT_QUARKUS_REST;
  }

  @Override
  public String getApplication() {
    return "quarkus-rest";
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("quarkus-rest 0.0.0-SNAPSHOT on JVM (powered by Quarkus 2.8.1.Final) started in", 60);
    awaitService(this, pod.getMetadata().getNamespace())
      .assertIsNodePort()
      .assertExposed()
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http",
        equalTo("{\"applicationName\":\"JKube\",\"message\":\"Subatomic JKube really whips the llama's ass!\"}"));
    return pod;
  }

  final void assertHelm(File helmDirectory) {
    assertThat(helmDirectory.exists(), equalTo(true));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      aMapWithSize(4),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", "quarkus-rest"),
      hasEntry("description", "Quarkus REST JSON project")
    )));
    assertThat(new File(helmDirectory, "values.yaml"), yaml(anEmptyMap()));
    assertThat(new File(helmDirectory, "templates/quarkus-rest-service.yaml"), yaml(not(anEmptyMap())));
  }
}

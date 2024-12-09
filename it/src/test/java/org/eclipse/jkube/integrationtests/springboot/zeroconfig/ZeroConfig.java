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
package org.eclipse.jkube.integrationtests.springboot.zeroconfig;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.io.File;

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

//@KubernetesTest(createEphemeralNamespace = false)
abstract class ZeroConfig implements JKubeCase {

  protected static final String MAVEN_APPLICATION = "spring-boot-zero-config";
  protected static final String GRADLE_APPLICATION = "sb-zero-config";
  protected static final String MAVEN_PROJECT_ZERO_CONFIG = "projects-to-be-tested/maven/spring/zero-config";

  private static KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    // TODO: REMOVE
    return new KubernetesClientBuilder().build();
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started ZeroConfigApplication in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, false);
    return pod;
  }

  static void assertHelm(File helmDirectory) {
    assertThat(helmDirectory.exists(), equalTo(true));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      aMapWithSize(5),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", "spring-boot-zero-config"),
      hasEntry("description", "Spring Boot with Zero Config project")
    )));
    assertThat(new File(helmDirectory, "values.yaml"), yaml(anEmptyMap()));
    assertThat(new File(helmDirectory, "templates/spring-boot-zero-config-service.yaml"), yaml(not(anEmptyMap())));
  }

}

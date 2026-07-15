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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.io.File;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

abstract class Watch implements JKubeCase {

  static final String MAVEN_APPLICATION = "spring-boot-watch";
  static final String GRADLE_APPLICATION = "sb-watch";

  static KubernetesClient kubernetesClient;
  File fileToChange;
  String originalFileContent;
  Pod originalPod;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  final Pod assertThatShouldApplyResources(String expectedMessage) throws Exception {
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

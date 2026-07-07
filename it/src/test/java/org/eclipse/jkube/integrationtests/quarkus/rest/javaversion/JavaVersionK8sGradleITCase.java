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
package org.eclipse.jkube.integrationtests.quarkus.rest.javaversion;

import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class JavaVersionK8sGradleITCase {

  @Gradle(project = "quarkus-rest")
  private JKubeGradleRunner gradle;

  @Test
  @Order(1)
  @DisplayName("k8sBuild with jkube.java.version=21, should use jkube-java21 base image")
  void k8sBuildWithJavaVersion21() {
    // When
    final var result = gradle.tasks("-Pjkube.java.version=21", "k8sBuild").build();
    // Then
    assertThat(result.getOutput(), containsString("jkube-java-21"));
  }
}

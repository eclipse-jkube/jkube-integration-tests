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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
class JettyK8sWatchNoneITCase extends JettyK8sWatch {

  @Override
  public String getProject() {
    return PROJECT_JETTY_WATCH + "-none";
  }

  @Override
  public String getApplication() {
    return APPLICATION_JETTY_WATCH + "-none";
  }

  @Test
  @DisplayName("k8s:watch, with mode=none, SHOULD NOT hot deploy the application")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchNone() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync(
        "k8s:watch", properties("jkube.watch.mode", "none"), baos, null);
      await(baos::toString).apply(log -> log.contains("Waiting ...")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, "<html><body><h2>Eclipse JKube Jetty v2</h2></body></html>", StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
      // Then
      assertThat(baos.toString(StandardCharsets.UTF_8), endsWith("Waiting ...\n"));
      assertThatShouldApplyResources("<h2>Eclipse JKube on Jetty rocks!</h2>");
    }
  }

}

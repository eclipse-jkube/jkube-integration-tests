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
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@Isolated // mvn k8s:watch doesn't behave well when run in parallel - TODO - further investigation needed in WatchService and its executors
class JettyK8sWatchCopyITCase extends JettyK8sWatch {

  @Override
  public String getProject() {
    return PROJECT_JETTY_WATCH + "-copy";
  }

  @Override
  public String getApplication() {
    return APPLICATION_JETTY_WATCH + "-copy";
  }

  @Test
  @DisplayName("k8s:watch, with mode=copy, SHOULD hot deploy the application")
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  void k8sWatchCopy() throws Exception {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Given
      mavenWatch = mavenAsync("k8s:watch", null, baos, null);
      await(baos::toString).apply(log -> log.contains("Waiting ...")).get(2, TimeUnit.MINUTES);
      // When
      FileUtils.write(fileToChange, "<html><body><h2>Eclipse JKube Jetty v2</h2></body></html>", StandardCharsets.UTF_8);
      assertInvocation(maven("package"));
      // Then
      try {
        await(baos::toString)
          .apply(log -> log.contains("Files successfully copied to the container."))
          .get(30, TimeUnit.SECONDS);
      } catch (TimeoutException ex) {
        throw new AssertionError("Expected message containing: 'Files successfully copied to the container.' but got: \n\n" + baos, ex);
      }
      waitUntilApplicationRestartsInsidePod();
      assertThatShouldApplyResources("<h2>Eclipse JKube Jetty v2</h2>");
    }
  }

}

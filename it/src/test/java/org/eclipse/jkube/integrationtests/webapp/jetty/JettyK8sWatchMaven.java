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

import io.fabric8.junit.jupiter.api.KubernetesTest;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;

@KubernetesTest(createEphemeralNamespace = false)
abstract class JettyK8sWatchMaven extends JettyK8sWatch implements MavenCase {

  Future<MavenInvocationResult> mavenWatch;

  @BeforeEach
  void setUp() throws Exception {
    fileToChange = new File(String.format("../%s/src/main/webapp/index.html", getProject()));
    originalFileContent = FileUtils.readFileToString(fileToChange, StandardCharsets.UTF_8);
    assertInvocation(maven("clean package k8s:build k8s:resource k8s:apply"));
    originalPod = assertThatShouldApplyResources("<h2>Eclipse JKube on Jetty rocks!</h2>");
  }

  @AfterEach
  void tearDown() throws IOException, MavenInvocationException, InterruptedException {
    try {
      if (mavenWatch != null) {
        mavenWatch.cancel(true);
      }
      if (originalPod != null) {
        kubernetesClient.resource(originalPod).withGracePeriod(0).delete();
      }
      assertInvocation(maven("k8s:undeploy"));
    } finally {
      FileUtils.write(fileToChange, originalFileContent, StandardCharsets.UTF_8);
    }
  }
}

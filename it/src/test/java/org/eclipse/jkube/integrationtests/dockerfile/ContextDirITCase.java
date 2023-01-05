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
package org.eclipse.jkube.integrationtests.dockerfile;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(KUBERNETES)
public class ContextDirITCase implements JKubeCase, MavenCase {

  private static final String PROJECT_CONTEXT_DIR = "projects-to-be-tested/maven/dockerfile/context-dir";

  @Override
  public KubernetesClient getKubernetesClient() {
    throw new UnsupportedOperationException("Not required for this test");
  }

  @Override
  public String getApplication() {
    return "dockerfile-context-dir";
  }

  @Override
  public String getProject() {
    return PROJECT_CONTEXT_DIR;
  }

  @Test
  @DisplayName("k8s:build, should create image with docker file and files read from provided contextDir configuration")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests", getApplication());
    final File targetDockerfile = new File(
      String.format("../%s/target/docker/integration-tests/%s/latest/build/Dockerfile", getProject(), getApplication()));
    final File expectedDockerfile = new File(String.format("../%s/src/main/docker-context-dir/Dockerfile", getProject()));
    assertTrue(FileUtils.contentEquals(expectedDockerfile, targetDockerfile));
    final List<String> imageFiles = listImageFiles(String.format("%s/%s", "integration-tests", getApplication()),
      "/deployments");
    assertThat(imageFiles, hasItem("/deployments/file-in-context.txt"));
    assertThat(imageFiles, not(hasItem("/deployments/other-file-to-ignore.txt")));
    assertThat(imageFiles, hasItem("/deployments/subdirectory/f1.txt"));
    assertThat(imageFiles, hasItem("/deployments/subdirectory/f2.txt"));
  }
}

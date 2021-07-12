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
package org.eclipse.jkube.integrationtests.windows;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.docker.RegistryExtension;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.jkube.integrationtests.Tags.WINDOWS;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

@Tag(WINDOWS)
@ExtendWith(RegistryExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WindowsITCase extends BaseMavenCase {

  private static final String PROJECT_WINDOWS = "projects-to-be-tested\\windows";

  @Override
  public String getProject() {
    return PROJECT_WINDOWS;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests","windows");
    final File dockerDirectory = new File(
      String.format("..\\%s\\target\\docker\\integration-tests\\windows\\latest", getProject()));
    assertThat(dockerDirectory.exists(), equalTo(true));
    assertThat(new File(dockerDirectory, "tmp\\docker-build.tar").exists(), equalTo(true));
    assertThat(new File(dockerDirectory, "build\\Dockerfile").exists(), equalTo(true));
    final String dockerFileContent = String.join("\n",
      Files.readAllLines(new File(dockerDirectory, "build\\Dockerfile").toPath()));
    assertThat(dockerFileContent, containsString("FROM mcr.microsoft.com/windows/nanoserver:1809-amd64"));
    final Matcher deploymentDirMatcher = Pattern.compile("COPY ([^\\s]*)", Pattern.MULTILINE).matcher(dockerFileContent);
    assertThat(deploymentDirMatcher.find(), equalTo(true));
    assertThat(new File(dockerDirectory,
        String.format("build\\%s\\windows-0.0.0-SNAPSHOT.jar", deploymentDirMatcher.group(1))).exists(), equalTo(true));
  }

  @Test
  @Order(2)
  @DisplayName("k8s:push, should push image to remote registry")
  void k8sPush() throws Exception {
    // Given
    final Properties properties = properties("jkube.docker.push.registry", "localhost:5000");
    // When
    final InvocationResult invocationResult = maven("k8s:push", properties);
    // Then
    assertInvocation(invocationResult);
    final Response response = new OkHttpClient.Builder().build().newCall(new Request.Builder()
      .get().url("http://localhost:5000/v2/integration-tests/windows/tags/list").build())
      .execute();
    assertThat(response.body().string(),
      containsString("{\"name\":\"integration-tests/windows\",\"tags\":[\"latest\"]}"));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("..\\%s\\target\\classes\\META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube\\kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube\\kubernetes\\windows-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube\\kubernetes\\windows-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(4)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("..\\%s\\target\\classes\\META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube\\openshift.yml"));
    assertThat(new File(metaInfDirectory, "jkube\\openshift\\windows-deploymentconfig.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube\\openshift\\windows-route.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube\\openshift\\windows-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(5)
  @DisplayName("k8s:helm, should create Helm charts")
  void k8sHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:helm");
    // Then
    assertInvocation(invocationResult);
    assertThat( new File(String.format("..\\%s\\target\\windows-0.0.0-SNAPSHOT-helm.tar.gz", getProject()))
      .exists(), equalTo(true));
    final File helmDirectory = new File(
      String.format("..\\%s\\target\\jkube\\helm\\windows\\kubernetes", getProject()));
    assertHelm(helmDirectory);
    assertThat(new File(helmDirectory, "templates\\windows-deployment.yaml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(6)
  @DisplayName("oc:helm, should create Helm charts")
  void ocHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:helm");
    // Then
    assertInvocation(invocationResult);
    assertThat( new File(String.format("..\\%s\\target\\windows-0.0.0-SNAPSHOT-helmshift.tar.gz", getProject()))
      .exists(), equalTo(true));
    final File helmDirectory = new File(
      String.format("..\\%s\\target\\jkube\\helm\\windows\\openshift", getProject()));
    assertHelm(helmDirectory);
    assertThat(new File(helmDirectory, "templates\\windows-deploymentconfig.yaml"), yaml(not(anEmptyMap())));
    assertThat(new File(helmDirectory, "templates\\windows-route.yaml"), yaml(not(anEmptyMap())));
  }

  @Override
  protected MavenInvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {
    return super.maven(goal, properties, ir -> ir.setProfiles(Collections.singletonList("Windows")));
  }

  private static void assertHelm(File helmDirectory) {
    assertThat(helmDirectory.exists(), equalTo(true));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      aMapWithSize(4),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", "windows"),
      hasEntry("description", "Windows Image Build")
    )));
    assertThat(new File(helmDirectory, "values.yaml"), yaml(anEmptyMap()));
    assertThat(new File(helmDirectory, "templates/windows-service.yaml"), yaml(not(anEmptyMap())));
  }

}

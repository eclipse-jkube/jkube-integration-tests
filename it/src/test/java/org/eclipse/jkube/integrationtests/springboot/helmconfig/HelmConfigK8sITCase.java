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
package org.eclipse.jkube.integrationtests.springboot.helmconfig;

import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistryHost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@DockerRegistry(port = 5080)
@Tag(KUBERNETES)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelmConfigK8sITCase extends HelmConfig {

  @DockerRegistryHost
  protected String registry;

  @Test
  @Order(1)
  @DisplayName("k8s:resource, no specified profile, should create default resource manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
  }

  @Test
  @Order(2)
  @DisplayName("k8s:helm, should create Helm charts")
  void k8sHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:helm");
    // Then
    assertInvocation(invocationResult);
    assertThat(new File(String.format("../%s/target/jkube/helm/the-chart-name/kubernetes/the-chart-name-1.0-KUBERNETES.tar", getProject()))
      .exists(), equalTo(true));
    final File helmDirectory = new File(
      String.format("../%s/target/jkube/helm/the-chart-name/kubernetes", getProject()));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      aMapWithSize(10),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", "the-chart-name"),
      hasEntry("version", "1.0-KUBERNETES"),
      hasEntry("description", "Description different to that in the pom.xml"),
      hasEntry("home", "https://www.home.example.com"),
      hasEntry("engine", "v8")
    )));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      hasEntry(is("sources"), contains("https://source.1.example.com")),
      hasEntry(is("keywords"), containsInAnyOrder("key", "words", "comma", "separated"))
    )));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(
      hasEntry(is("maintainers"), contains(allOf(
        hasEntry("name", "Mr. Ed"),
        hasEntry("email", "ed@example.com")
      )))
    ));
    assertThat(new File(helmDirectory, "values.yaml"), yaml(anEmptyMap()));
    assertThat(new File(helmDirectory, "templates/spring-boot-helm-config-deployment.yaml"), yaml(not(anEmptyMap())));
    assertThat(new File(helmDirectory, "templates/spring-boot-helm-config-service.yaml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:helm-push, should push the charts")
  void k8sHelmPush() throws Exception {
    // Given
    final Properties properties = properties(
      "jkube.helm.stableRepository.type", "OCI",
      "jkube.helm.stableRepository.name", "docker",
      "jkube.helm.stableRepository.url", "oci://" + registry,
      "jkube.helm.stableRepository.username", "ignored",
      "jkube.helm.stableRepository.password", "ignored"
    );
    // When
    final InvocationResult invocationResult = maven("k8s:helm-push", properties);
    // Then
    assertInvocation(invocationResult);
    assertThat(httpGet("http://" + registry + "/v2/the-chart-name/tags/list").body(),
      containsString("{\"name\":\"the-chart-name\",\"tags\":[\"1.0-KUBERNETES\"]}"));
  }

  @Test
  @DisplayName("k8s:helm-uninstall, no release present, display error message")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void k8sHelmUninstall_whenNoReleasePresent_thenErrorMessageDisplayed() throws Exception {
    // Given
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    // When
    maven("k8s:helm-uninstall", properties("jkube.helm.release.name", "spring-boot-helm-config-k8s-does-not-exist"), byteArrayOutputStream);
    // Then
    assertThat(byteArrayOutputStream.toString(), allOf(
      containsString("helm-uninstall failed"),
      containsString("uninstall: Release not loaded: spring-boot-helm-config-k8s-does-not-exist: release: not found")
    ));
  }

  @Test
  @Order(4)
  @DisplayName("k8s:helm-install, should install the charts")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void k8sHelmInstall() throws Exception {
    // Given
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    // When
    maven("k8s:helm-install", properties("jkube.helm.release.name", "spring-boot-helm-config-k8s"), byteArrayOutputStream);
    // Then
    assertThat(byteArrayOutputStream.toString(),
      allOf(containsString("Installing Helm Chart the-chart-name 1.0-KUBERNETES"),
        containsString("NAME: spring-boot-helm-config-k8s"),
        containsString("NAMESPACE: "),
        containsString("STATUS: deployed"),
        containsString("REVISION: 1")));
  }

  @Test
  @Order(5)
  @DisplayName("k8s:helm-uninstall, should uninstall the charts")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void k8sHelmUninstall() throws Exception {
    // Given
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    // When
    maven("k8s:helm-uninstall", properties("jkube.helm.release.name", "spring-boot-helm-config-k8s"), byteArrayOutputStream);
    // Then
    assertThat(byteArrayOutputStream.toString(),
      allOf(containsString("Uninstalling Helm Chart the-chart-name 1.0-KUBERNETES"),
        containsString("release \"spring-boot-helm-config-k8s\" uninstalled")));
  }
}

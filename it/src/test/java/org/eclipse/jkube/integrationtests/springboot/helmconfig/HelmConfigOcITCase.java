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

import io.fabric8.junit.jupiter.api.KubernetesTest;
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

import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT_OSCI;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@Tag(OPEN_SHIFT)
@Tag(OPEN_SHIFT_OSCI)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DockerRegistry(port = 5080)
class HelmConfigOcITCase extends HelmConfig {

  @DockerRegistryHost
  protected String registry;

  @Test
  @Order(1)
  @DisplayName("oc:resource, no specified profile, should create default resource manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertInvocation(invocationResult);
  }

  @Test
  @Order(2)
  @DisplayName("oc:helm, should create Helm charts")
  void ocHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:helm");
    // Then
    assertInvocation(invocationResult);
    assertThat(new File(String.format("../%s/target/jkube/helm/different-name-for-oc/openshift/different-name-for-oc-0.1-OC.zip", getProject()))
      .exists(), equalTo(true));
    final File helmDirectory = new File(
      String.format("../%s/target/jkube/helm/different-name-for-oc/openshift", getProject()));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      aMapWithSize(9),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", "different-name-for-oc"),
      hasEntry("version", "0.1-OC"),
      hasEntry("description", "Spring Boot with Helm Config project"),
      hasEntry("home", "https://www.home.example.com/open-shift"),
      hasEntry("engine", "EV-1")
    )));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(
      hasEntry(is("sources"), contains("https://redhat.example.com"))
    ));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(
      hasEntry(is("maintainers"), contains(allOf(
        hasEntry("name", "Mr. Red"),
        hasEntry("email", "red@example.com")
      )))
    ));
    assertThat(new File(helmDirectory, "values.yaml"), yaml(anEmptyMap()));
    assertThat(new File(helmDirectory, "templates/spring-boot-helm-config-deploymentconfig.yaml"), yaml(not(anEmptyMap())));
    assertThat(new File(helmDirectory, "templates/spring-boot-helm-config-route.yaml"), yaml(not(anEmptyMap())));
    assertThat(new File(helmDirectory, "templates/spring-boot-helm-config-service.yaml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @DisplayName("oc:helm-push, should push the charts")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void ocHelmPush() throws Exception {
    // Given
    final Properties properties = properties(
      "jkube.helm.stableRepository.type", "OCI",
      "jkube.helm.stableRepository.name", "docker",
      "jkube.helm.stableRepository.url", "oci://" + registry,
      "jkube.helm.stableRepository.username", "ignored",
      "jkube.helm.stableRepository.password", "ignored"
    );
    // When
    final InvocationResult invocationResult = maven("oc:helm-push", properties);
    // Then
    assertInvocation(invocationResult);
    assertThat(httpGet("http://" + registry + "/v2/different-name-for-oc/tags/list").body(),
      containsString("{\"name\":\"different-name-for-oc\",\"tags\":[\"0.1-OC\"]}"));
  }

  @Test
  @DisplayName("oc:helm-uninstall, no release present, display error message")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void ocHelmUninstall_whenNoReleasePresent_thenErrorMessageDisplayed() throws Exception {
    // Given
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    // When
    maven("oc:helm-uninstall", properties("jkube.helm.release.name", "spring-boot-helm-config-oc-does-not-exist"), byteArrayOutputStream);
    // Then
    assertThat(byteArrayOutputStream.toString(), allOf(
      containsString("helm-uninstall failed"),
      containsString("uninstall: Release not loaded: spring-boot-helm-config-oc-does-not-exist: release: not found")
    ));
  }

  @Test
  @Order(4)
  @DisplayName("oc:helm-install, should install the charts")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void ocHelmInstall() throws Exception {
    // Given
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    // When
    maven("oc:helm-install", properties("jkube.helm.release.name", "spring-boot-helm-config-oc"), byteArrayOutputStream);
    // Then
    assertThat(byteArrayOutputStream.toString(),
      allOf(containsString("Installing Helm Chart different-name-for-oc 0.1-OC"),
        containsString("NAME: spring-boot-helm-config-oc"),
        containsString("NAMESPACE: "),
        containsString("STATUS: deployed"),
        containsString("REVISION: 1")));
  }

  @Test
  @Order(5)
  @DisplayName("oc:helm-uninstall, should uninstall the charts")
  @DisabledIfEnvironmentVariable(named = "ImageOS", matches = "ubuntu20")
  void ocHelmUninstall() throws Exception {
    // Given
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    // When
    maven("oc:helm-uninstall", properties("jkube.helm.release.name", "spring-boot-helm-config-oc"), byteArrayOutputStream);
    // Then
    assertThat(byteArrayOutputStream.toString(),
      allOf(containsString("Uninstalling Helm Chart different-name-for-oc 0.1-OC"),
        containsString("release \"spring-boot-helm-config-oc\" uninstalled")));
  }
}

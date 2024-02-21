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
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistryHost;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT_OSCI;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DockerRegistry(port = 5080)
@KubernetesTest(createEphemeralNamespace = false)
class HelmConfigITCase implements JKubeCase, MavenCase {

  private static final String PROJECT_HELM = "projects-to-be-tested/maven/spring/helm-config";

  private static KubernetesClient kubernetesClient;

  @DockerRegistryHost
  private String registry;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }
  @Override
  public String getProject() {
    return PROJECT_HELM;
  }

  @Test
  @Order(1)
  @Tag(KUBERNETES)
  @DisplayName("k8s:resource, no specified profile, should create default resource manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
  }

  @Test
  @Order(1)
  @Tag(OPEN_SHIFT)
  @Tag(OPEN_SHIFT_OSCI)
  @DisplayName("oc:resource, no specified profile, should create default resource manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertInvocation(invocationResult);
  }

  @Test
  @Order(2)
  @Tag(KUBERNETES)
  @DisplayName("k8s:helm, should create Helm charts")
  void k8sHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:helm");
    // Then
    assertInvocation(invocationResult);
    assertThat(new File(String.format("../%s/target/jkube/helm/This is the chart name/kubernetes/This is the chart name-1.0-KUBERNETES.tar", getProject()))
      .exists(), equalTo(true));
    final File helmDirectory = new File(
      String.format("../%s/target/jkube/helm/This is the chart name/kubernetes", getProject()));
    assertThat(new File(helmDirectory, "Chart.yaml"), yaml(allOf(
      aMapWithSize(10),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", "This is the chart name"),
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
  @Order(2)
  @Tag(OPEN_SHIFT)
  @Tag(OPEN_SHIFT_OSCI)
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
  @Tag(KUBERNETES)
  @DisplayName("k8s:helm-push, should push the charts")
  void k8sHelmPush() throws Exception {
    // Given
    final Properties properties = properties(
      "jkube.helm.chart", "the-chart-name",
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
  @Order(3)
  @Tag(OPEN_SHIFT)
  @Tag(OPEN_SHIFT_OSCI)
  @DisplayName("oc:helm-push, should push the charts")
  void ocHelmPush() throws Exception {
    // Given
    final Properties properties = properties(
      "jkube.helm.chart", "the-chart-name",
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
    assertThat(httpGet("http://" + registry + "/v2/the-chart-name/tags/list").body(),
      containsString("{\"name\":\"the-chart-name\",\"tags\":[\"0.1-OC\"]}"));
  }
}

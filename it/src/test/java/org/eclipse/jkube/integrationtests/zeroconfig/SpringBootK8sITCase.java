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
package org.eclipse.jkube.integrationtests.zeroconfig;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.docker.DockerUtils;
import org.eclipse.jkube.integrationtests.docker.DockerUtils.DockerImage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class SpringBootK8sITCase extends SpringBoot {

  private KubernetesClient k;

  @BeforeEach
  void setUp() {
    k = new DefaultKubernetesClient();
  }

  @AfterEach
  void tearDown() {
    k = null;
  }

  @Test
  @Order(1)
  void build_zeroConf_shouldCreateImage() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final List<DockerImage> dockerImages = DockerUtils.dockerImages();
    assertThat(dockerImages, hasSize(greaterThanOrEqualTo(1)));
    final DockerImage mostRecentImage = dockerImages.iterator().next();
    assertThat(mostRecentImage.getRepository(),
      equalTo("integration-tests/zero-config-spring-boot"));
    assertThat(mostRecentImage.getTag(), equalTo("latest"));
    assertThat(mostRecentImage.getCreatedSince(), containsString("second"));
  }

  @Test
  @Order(2)
  void resource_zeroConf_shouldCreateResources() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
        String.format("../%s/target/classes/META-INF", PROJECT_ZERO_CONFIG));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/zero-config-spring-boot-deployment.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/zero-config-spring-boot-service.yml"). exists(), equalTo(true));
  }

  @Test
  @Order(3)
  void apply_zeroConf_shouldApplyResources() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldApplyResources(k);
    final Optional<Deployment> deployment = k.apps().deployments().list().getItems().stream()
      .filter(d -> d.getMetadata().getName().startsWith("zero-config-spring-boot"))
      .findFirst();
    assertThat(deployment.isPresent(), equalTo(true));
    assertThat(deployment.get().getMetadata().getLabels(), hasEntry("app", "zero-config-spring-boot"));
    assertThat(deployment.get().getMetadata().getLabels(), hasEntry("provider", "jkube"));
    assertThat(deployment.get().getMetadata().getLabels(), hasEntry("group", "org.eclipse.jkube.integration-tests"));
    final DeploymentSpec deploymentSpec = deployment.get().getSpec();
    assertThat(deploymentSpec.getReplicas(), equalTo(1));
    assertThat(deploymentSpec.getSelector().getMatchLabels(),  hasEntry("app", "zero-config-spring-boot"));
    assertThat(deploymentSpec.getSelector().getMatchLabels(), hasEntry("provider", "jkube"));
    assertThat(deploymentSpec.getSelector().getMatchLabels(), hasEntry("group", "org.eclipse.jkube.integration-tests"));
    final PodTemplateSpec ptSpec = deploymentSpec.getTemplate();
    assertThat(ptSpec.getMetadata().getLabels(), hasEntry("app", "zero-config-spring-boot"));
    assertThat(ptSpec.getMetadata().getLabels(), hasEntry("provider", "jkube"));
    assertThat(ptSpec.getMetadata().getLabels(), hasEntry("group", "org.eclipse.jkube.integration-tests"));
    assertThat(ptSpec.getSpec().getContainers(), hasSize(1));
    final Container ptContainer = ptSpec.getSpec().getContainers().iterator().next();
    assertThat(ptContainer.getImage(), equalTo("integration-tests/zero-config-spring-boot:latest"));
    assertThat(ptContainer.getName(), equalTo("spring-boot"));
    assertThat(ptContainer.getPorts(), hasSize(3));
    assertThat(ptContainer.getPorts(), hasItems(allOf(
      hasProperty("name", equalTo("http")),
      hasProperty("containerPort", equalTo(8080))
    )));
  }

  @Test
  @Order(4)
  void undeploy_zeroConf_shouldDeleteAllAppliedResources() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(k);
    final boolean deploymentsExists = k.apps().deployments().list().getItems().stream()
      .anyMatch(d -> d.getMetadata().getName().startsWith("zero-config-spring-boot"));
    assertThat(deploymentsExists, equalTo(false));
  }

  @Override
  final InvocationResult maven(String goal)
      throws IOException, InterruptedException, MavenInvocationException {

    final Properties properties = new Properties();
    properties.setProperty("jkube.mode", "kubernetes");
    return maven(goal, properties);
  }
}

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
package org.eclipse.jkube.integrationtests.springboot.complete;

import org.apache.maven.shared.invoker.InvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Locks.SPRINGBOOT_COMPLETE_K8s;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.getImageHistory;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class CompleteK8sITCase extends Complete {

  @Override
  public List<String> getProfiles() {
    return Collections.singletonList(KUBERNETES);
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests", getApplication());
    final List<String> imageFiles = listImageFiles(String.format("%s/%s", "integration-tests", getApplication()),
      "/deployments");
    assertThat(imageFiles, not(hasItem("/deployments/assembly-test")));
    assertThat(imageFiles, not(hasItem("/deployments/static")));
    assertThat(imageFiles, hasItem("/deployments/will-be-included-if-no-assemblies-defined.txt"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/lib"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/classes"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/classpath.idx"));
    assertThat(imageFiles, hasItem("/deployments/BOOT-INF/layers.idx"));
    assertThat(imageFiles, hasItem("/deployments/org/springframework/boot/loader/JarLauncher.class"));
    final List<String> imageHistory = getImageHistory(String.format("%s/%s", "integration-tests", getApplication()));
    long dirCopyLayers = imageHistory.stream()
      .filter(l -> l.contains("COPY dir:"))
      .count();
    assertThat(dirCopyLayers, equalTo(4L));
  }

  @Test
  @Order(2)
  @ResourceLock(value = SPRINGBOOT_COMPLETE_K8s, mode = READ_WRITE)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/password-secret.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/spring-boot-complete-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/spring-boot-complete-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service")
  @SuppressWarnings("unchecked")
  void k8sApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertInvocation(invocationResult);
    final String namespace = assertThatShouldApplyResources()
      .assertNodePortResponse("us-cli",
        containsString("This project contains various building blocks for the jkube developer toolbox"),
        "jkube", "readme")
      .getKubernetesResource().getMetadata().getNamespace();
    awaitDeployment(this, namespace)
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", equalTo("integration-tests/spring-boot-complete:latest")),
        hasProperty("name", equalTo("spring-boot")),
        hasProperty("ports", hasSize(3)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("us-cli")),
          hasProperty("containerPort", equalTo(8082))
        )))
      )));
  }

  @Test
  @Order(4)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertInvocation(invocationResult);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
  }
}

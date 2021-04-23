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
package org.eclipse.jkube.integrationtests.karaf;


import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.assertDeploymentExists;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class KarafK8sITCase extends Karaf {

  private KubernetesClient k;

  @BeforeEach
  void setUp(){ k= new DefaultKubernetesClient();}

  @AfterEach
  void tearDown(){
    k.close();
    k = null;
  }

  @Override
  public KubernetesClient getKubernetesClient(){ return k;}

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception{
    //When
    final InvocationResult invocationResult = maven("k8s:build");
    //Then
    assertThat(invocationResult.getExitCode(), equalTo(0));
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertImageWasRecentlyBuilt("integration-tests", getApplication());
  }

  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifest for Kubernetes")
  void k8sResource() throws Exception{
    //When
    final InvocationResult invocationResult = maven("k8s:resource");
    //Then
    assertThat(invocationResult.getExitCode(),equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
      assertThat(metaInfDirectory.exists(), equalTo(true));
      assertListResource(new File(metaInfDirectory,"jkube/kubernetes.yml"));
      assertThat(new File(metaInfDirectory, "jkube/kubernetes/karaf-camel-log-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/karaf-camel-log-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = ResourceAccessMode.READ_WRITE)
  @DisplayName("k8s:apply, should apply manifests on k8s cluster")
  void K8sApply() throws Exception {
    //When
    final InvocationResult invocationResult = maven("k8s:apply");
    //Then
    assertThat(invocationResult.getExitCode(), equalTo(0));
    final Pod pod = assertThatShouldApplyResources();
    awaitDeployment(this, pod.getMetadata().getNamespace())
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", equalTo("integration-tests/karaf-camel-log:latest")),
        hasProperty("name", equalTo("karaf")),
        hasProperty("ports", hasSize(2)),
        hasProperty("ports", hasItems(
          allOf(
            hasProperty("name", equalTo("intermapper")),
            hasProperty("containerPort", equalTo(8181))
          ),
          allOf(
            hasProperty("name", equalTo("jolokia")),
            hasProperty("containerPort", equalTo(8778))
          )
        ))
      )));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:log")
  void k8sLog() throws  Exception {
    //When
    final MavenInvocationResult invocationResult = maven("k8s:log", properties("jkube.log.follow", "false"));
    //Then
    assertThat(invocationResult.getExitCode(), equalTo(0));
    assertLog(invocationResult.getStdOut());
  }

  @Test
  @Order(4)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws  Exception{
    //When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    //Then
    assertThat(invocationResult.getExitCode(),equalTo(0));
    assertThatShouldDeleteAllAppliedResources(this);
    assertDeploymentExists(this,equalTo(false));
  }

}

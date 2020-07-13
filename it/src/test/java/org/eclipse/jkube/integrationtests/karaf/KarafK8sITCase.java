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
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import okhttp3.Response;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.assertDeploymentExists;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(KUBERNETES)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KarafK8sITCase extends Karaf{
  private KubernetesClient k;

  @BeforeEach
  void setUp(){ k= new DefaultKubernetesClient();}

  @AfterEach
  void tearDown(){
    k.close();
    k=null;
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
    assertImageWasRecentlyBuilt("integration-tests", "karaf-camel-log");
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
      String.format("../%s/target/classes/META-INF",PROJECT_KARAF));
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
        hasProperty("ports", hasSize(1)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("intermapper")),
          hasProperty("containerPort", equalTo(8181))
        )))
      )));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:log")
  void k8sLog() throws  Exception{
    //Given
    final Properties properties = new Properties();
    properties.setProperty("jkube.log.follow", "false");
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final MavenUtils.InvocationRequestCustomizer irc =  invocationRequest -> {
      invocationRequest.setOutputHandler(new PrintStreamHandler(new PrintStream(baos),true));
    };
    //When
    final InvocationResult invocationResult = maven("k8s:log",properties,irc);
    //Then
    assertThat(invocationResult.getExitCode(),equalTo(0));
    assertLog(baos.toString(StandardCharsets.UTF_8));
    assertLogFile(k);
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


  public void assertLogFile(KubernetesClient k) throws Exception {
    final CountDownLatch cdl = new CountDownLatch(1);
    final ExecListener waitToComplete = new ExecListener() {
      @Override
      public void onOpen(Response response) {
      }

      @Override
      public void onFailure(Throwable t, Response response) {
        cdl.countDown();
      }

      @Override
      public void onClose(int code, String reason) {
        cdl.countDown();
      }
    };
    try (
      final ByteArrayOutputStream result = new ByteArrayOutputStream()
    ) {
      final Pod pod = assertThatShouldApplyResources();
      PodList karafPodList = k.pods().inNamespace(pod.getMetadata().getNamespace()).withLabel("app", "karaf-camel-log").list();
      assertEquals(1, karafPodList.getItems().size());
      Pod karafAppPod = karafPodList.getItems().get(0);
      k.pods().inNamespace(pod.getMetadata().getNamespace()).withName(karafAppPod.getMetadata().getName())
        .readingInput(System.in)
        .writingOutput(result)
        .writingError(System.out)
        .withTTY()
        .usingListener(waitToComplete)
        .exec("cat", "/deployments/karaf/work/orders/processed/order1.xml");
      assertTrue(cdl.await(10, TimeUnit.SECONDS));
      assertThat(result.toString(StandardCharsets.UTF_8),stringContainsInOrder(" <customer id=","<name>","<city>","<country>"));
    }
  }
}

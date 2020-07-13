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
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.Response;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.OpenShift;
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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.OpenShift.cleanUpCluster;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KarafOcITCase extends Karaf  {

  private OpenShiftClient oc;

  @BeforeEach
  void setUp() {oc = new DefaultKubernetesClient().adapt(OpenShiftClient.class); }

  @AfterEach
  void  tearDown(){
    oc.close();
    oc=null;
  }

  @Override
  public KubernetesClient getKubernetesClient() {return oc;}

  @Test
  @Order(1)
  @DisplayName("oc:build, should create image")
  void ocBuild() throws Exception {
    //When
    final InvocationResult invocationResult = maven("oc:build");
    //Then
    assertThat(invocationResult.getExitCode(),equalTo(0));
    final ImageStream is = oc.imageStreams().withName("karaf-camel-log").get();
    assertThat(is,notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(),equalTo("latest"));
  }

  @Test
  @Order(1)
  @DisplayName("oc:resource, should create resource manifests")
  void ocResource() throws Exception {
    //When
    final InvocationResult invocationResult = maven("oc:resource");
    //Then
    assertThat(invocationResult.getExitCode(), equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF",PROJECT_KARAF));
    assertThat(metaInfDirectory.exists(),equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/openshift.yml"));
    assertThat(new File(metaInfDirectory,"jkube/openshift/karaf-camel-log-deploymentconfig.yml"),yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory,"jkube/openshift/karaf-camel-log-service.yml"),yaml(not(anEmptyMap())));
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:apply, should create pod, service and route")
  void ocApply() throws Exception {
    //When
    final InvocationResult invocationResult = maven("oc:apply");
    //Then
    assertThat(invocationResult.getExitCode(), equalTo(0));
    assertThatShouldApplyResources();
  }

  @Test
  @Order(3)
  @DisplayName("oc:log, should retrive log")
  void ocLog() throws Exception{
    //Given
    final Properties properties = new Properties();
    properties.setProperty("jkube.log.follow","false");
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final MavenUtils.InvocationRequestCustomizer irc = invocationRequest -> {
      invocationRequest.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));    };
    //When
    final InvocationResult invocationResult= maven("oc:log", properties,irc);
    //Then
    assertThat(invocationResult.getExitCode(),equalTo(0));
    assertLog(baos.toString(StandardCharsets.UTF_8)
    );
    assertLogFile(oc);
  }

  @Test
  @Order(4)
  @DisplayName("oc:undeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(this);
    cleanUpCluster(oc, this);
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


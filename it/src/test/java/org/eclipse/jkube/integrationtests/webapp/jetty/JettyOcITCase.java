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
package org.eclipse.jkube.integrationtests.webapp.jetty;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.OpenShift.cleanUpCluster;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@TestMethodOrder(OrderAnnotation.class)
class JettyOcITCase extends Jetty {

  private OpenShiftClient oc;

  @BeforeEach
  void setUp() {
    oc = new DefaultKubernetesClient().adapt(OpenShiftClient.class);
  }

  @AfterEach
  void tearDown() {
    oc.close();
    oc = null;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return oc;
  }

  @Test
  @Order(1)
  @DisplayName("oc:build, should create image")
  void ocBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:build");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final ImageStream is = oc.imageStreams().withName(getApplication()).get();
    assertThat(is, notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(), equalTo("latest"));
  }

  @Test
  @Order(1)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/openshift.yml"));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-jetty-deploymentconfig.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-jetty-route.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-jetty-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:apply, should deploy pod and service")
  void ocApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldApplyResources();
  }

  @Test
  @Order(3)
  @DisplayName("oc:log, should retrieve log")
  void ocLog() throws Exception {
    // Given
    final Properties properties = new Properties();
    properties.setProperty("jkube.log.follow", "false");
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final MavenUtils.InvocationRequestCustomizer irc = invocationRequest -> {
      invocationRequest.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));
    };
    // When
    final InvocationResult invocationResult = maven("oc:log", properties, irc);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertLog(baos.toString(StandardCharsets.UTF_8));
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

}

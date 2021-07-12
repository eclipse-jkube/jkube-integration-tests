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
package org.eclipse.jkube.integrationtests.webapp.wildfly;


import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.Tags;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.OpenShift.cleanUpCluster;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(Tags.OPEN_SHIFT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WildFlyOcDockerModeITCase extends WildFly  {
  private OpenShiftClient oc;

  @Override
  public String getApplication() {
    return "webapp-wildfly-docker-mode";
  }

  private static final String DOCKER_MODE_PROFILE = "docker-mode";
  @Override
  protected List<String> getProfiles() {
    return Collections.singletonList(DOCKER_MODE_PROFILE);
  }

  @BeforeEach
  void setUp(){ oc = new DefaultOpenShiftClient().adapt(OpenShiftClient.class); }

  @AfterEach
  void tearDown(){
    oc.close();
    oc=null;
  }

  @Override
  public KubernetesClient getKubernetesClient() { return  oc; }

  @Test
  @Order(1)
  @DisplayName("oc:build, should create image using docker")
  void ocBuild() throws Exception{
   //When
   final InvocationResult invocationResult = maven("oc:build");
   //Then
    assertInvocation(invocationResult);
    final ImageStream is = oc.imageStreams().withName(getApplication()).get();
    assertThat(is, notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(),equalTo("latest"));
  }

  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifests")
  void ocResource() throws Exception{
    //When
    final InvocationResult invocationResult = maven("oc:resource");
    //Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", PROJECT_WILDFLY));
    assertThat(metaInfDirectory.exists(),equalTo(true));
    assertListResource(new File(metaInfDirectory,"/jkube-docker-mode/openshift.yml"));
    assertThat(new File(metaInfDirectory, "jkube-docker-mode/openshift/webapp-wildfly-docker-mode-deploymentconfig.yml"),yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube-docker-mode/openshift/webapp-wildfly-docker-mode-service.yml"),yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube-docker-mode/openshift/webapp-wildfly-docker-mode-route.yml"),yaml(not(anEmptyMap())));
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE,mode = READ_WRITE)
  @DisplayName("oc:apply, should deploy pod and service")
  void ocApply() throws Exception{
    //when
    final InvocationResult invocationResult = maven("oc:apply");
    //Then
    assertInvocation(invocationResult);
    assertThatShouldApplyResources();
  }

  @Test
  @Order(3)
  @DisplayName("oc:log, should retrieve logs")
  void ocLog() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("oc:log", properties("jkube.log.follow", "false"));
    // Then
    assertInvocation(invocationResult);
    assertThat(invocationResult.getStdOut(), allOf(
      not(containsString("Running wildfly/wildfly-centos7 image")),
      stringContainsInOrder("JBoss Bootstrap Environment", "Deployed \"ROOT.war\"")
    ));
  }

  @Test
  @Order(4)
  @DisplayName("oc:undeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    //When
    final InvocationResult invocationResult = maven("oc:undeploy");
    //Then
    assertInvocation(invocationResult);
    assertThatShouldDeleteAllAppliedResources(this);
    cleanUpCluster(oc, this);
  }
}

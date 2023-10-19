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
package org.eclipse.jkube.integrationtests.quarkus.rest;

import io.fabric8.openshift.api.model.ImageStream;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.OpenShiftCase;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT_OSCI;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.StringContainsInOrder.stringContainsInOrder;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@Tag(OPEN_SHIFT_OSCI)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuarkusOcTraceEnabledITCase extends Quarkus implements OpenShiftCase {

  @Override
  public String getProject() {
    return "projects-to-be-tested/maven/quarkus/rest-trace-logging-enabled";
  }

  @Override
  public String getApplication() {
    return "quarkus-rest-trace-logging-enabled";
  }

  @Test
  @Order(1)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:build, with org.slf4j.simpleLogger.defaultLogLevel=trace, should create image and print trace logs")
  void ocBuild() throws Exception {
    // Given
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // When
    final InvocationResult invocationResult = maven("clean package oc:build",
      properties("org.slf4j.simpleLogger.defaultLogLevel", "trace"), baos);
    // Then
    assertInvocation(invocationResult);
    String ocBuildLog = baos.toString();
    assertThat(ocBuildLog, containsString("[TRACE] -HTTP START-"));
    assertThat(ocBuildLog, containsString("[TRACE] -HTTP END-"));
    assertThat(ocBuildLog, containsString("[TRACE] -WS START-"));
    assertThat(ocBuildLog, containsString("[TRACE] -WS END-"));
    ImageStream is = getOpenShiftClient().imageStreams().withName(getApplication()).get();

    assertThat(is.getStatus().getTags().iterator().next().getTag(), equalTo("latest"));
    assertThat(getOpenShiftClient().imageStreams().withName(getApplication()).get(), notNullValue());
  }

  @Test
  @Order(2)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/openshift.yml"));
    assertThat(new File(metaInfDirectory, "jkube/openshift/quarkus-rest-trace-logging-enabled-deploymentconfig.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/openshift/quarkus-rest-trace-logging-enabled-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:apply, with org.slf4j.simpleLogger.defaultLogLevel=trace, should deploy pod and service and print trace logs")
  void ocApply() throws Exception {
    // Given
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    assertThat(getOpenShiftClient().imageStreams().withName(getApplication()).get(), notNullValue());
    // When
    final InvocationResult invocationResult = maven("oc:apply",
      properties("org.slf4j.simpleLogger.defaultLogLevel", "trace")
      , baos);
    // Then
    String ocApplyLog = baos.toString();
    assertThat(ocApplyLog, containsString("[TRACE] -HTTP START-"));
    assertThat(ocApplyLog, containsString("[TRACE] -HTTP END-"));
    assertInvocation(invocationResult);
    assertThatShouldApplyResources();
  }

  @Test
  @Order(4)
  @DisplayName("oc:log, with org.slf4j.simpleLogger.defaultLogLevel=trace, should retrieve log and print trace logs")
  void ocLog() throws Exception {
    // Given
    Properties properties = new Properties();
    properties.put("org.slf4j.simpleLogger.defaultLogLevel", "trace");
    properties.put("jkube.log.follow", "false");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // When
    final MavenInvocationResult invocationResult = maven("oc:log", properties, baos);
    // Then
    String ocLogGoalLog = baos.toString();
    assertThat(ocLogGoalLog, containsString("[TRACE] -HTTP START-"));
    assertThat(ocLogGoalLog, containsString("[TRACE] -HTTP END-"));
    assertThat(ocLogGoalLog, containsString("[TRACE] -WS START-"));
    assertThat(ocLogGoalLog, containsString("[TRACE] -WS END-"));
    assertInvocation(invocationResult);
    assertThat(invocationResult.getStdOut(),
      stringContainsInOrder(String.format("INFO: %s 0.0.0-SNAPSHOT on JVM", getApplication()), " started in "));
  }

  @Test
  @Order(5)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:undeploy, with org.slf4j.simpleLogger.defaultLogLevel=trace, should delete all applied resources and print trace logs")
  void ocUndeploy() throws Exception {
    // Given
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // When
    final InvocationResult invocationResult = maven("oc:undeploy",
      properties("org.slf4j.simpleLogger.defaultLogLevel", "trace"), baos);
    // Then
    String ocUndeployLog = baos.toString();
    assertThat(ocUndeployLog, containsString("[TRACE] -HTTP START-"));
    assertThat(ocUndeployLog, containsString("[TRACE] -HTTP END-"));
    assertInvocation(invocationResult);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources();
    cleanUpCluster();
  }
}

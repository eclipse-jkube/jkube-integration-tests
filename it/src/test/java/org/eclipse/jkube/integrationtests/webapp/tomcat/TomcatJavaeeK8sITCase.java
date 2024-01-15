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
package org.eclipse.jkube.integrationtests.webapp.tomcat;

import io.fabric8.kubernetes.api.model.Pod;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.maven.MavenInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.List;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.docker.DockerUtils.listImageFiles;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

/**
 * This test making sure that we support legacy Javaee apps in Tomcat. By
 * default, Tomcat 10 should migrate automatically JavaEE projects to JakartaEE.
 * This is also checking that option to disable the migration at start up works.
 * Legacy JavaEE apps SHOULD NOT work properly without the automated migration.
 */
@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class TomcatJavaeeK8sITCase extends Tomcat {

  @Override
  public String getProject() {
    return PROJECT_TOMCAT_JAVAEE;
  }

  @Override
  public String getApplication() {
    return APPLICATION_NAME_TOMCAT_JAVAEE;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build should display the Tomcat webapps dir hint by default")
  void k8sBuild() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests", getApplication());
    final List<String> imageFiles = listImageFiles(String.format("%s/%s", "integration-tests", getApplication()),
        "/deployments");
    assertThat(imageFiles, hasItem("/deployments/ROOT.war"));
    assertThat(invocationResult.getStdOut(), containsString(
        "[INFO] k8s: webapp: HINT: Tomcat webapps dir is set to `webapps-javaee` by default for retrocompatibility. If your project is already JakartaEE compliant, set `jkube.generator.webapp.env` to `TOMCAT_WEBAPPS_DIR=webapps` for a faster startup."));
  }

  @Test
  @Order(1)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(String.format("../%s/target/classes/META-INF", PROJECT_TOMCAT_JAVAEE));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/webapp-tomcat-javaee-legacy-deployment.yml"),
        yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/webapp-tomcat-javaee-legacy-service.yml"),
        yaml(not(anEmptyMap())));
  }

  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service")
  void k8sApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertInvocation(invocationResult);
    final Pod pod = assertThatShouldApplyResources();
    awaitDeployment(pod.getMetadata().getNamespace(), "integration-tests/webapp-tomcat-javaee-legacy:latest");
    awaitService(this, pod.getMetadata().getNamespace()) //
        .assertIsNodePort();
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("The JavaEE servlet, exposed as a NodePort Service, should return a string and contain `Hello World`")
  void testJavaeeNodePortResponse() throws Exception {
    final Pod pod = assertThatShouldApplyResources();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertNodePortResponse("http", containsString("Hello World"), "hello-world?name=World");
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:log, should retrieve log with migration notice")
  void k8sLog() throws Exception {
    // When
    final MavenInvocationResult invocationResult = maven("k8s:log", properties("jkube.log.follow", "false"));
    // Then
    assertInvocation(invocationResult);
    assertLogWithMigrationNotice(invocationResult.getStdOut());
  }

  @Test
  @Order(4)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeployJavaEE() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertInvocation(invocationResult);
    assertJKube(this) //
        .assertThatShouldDeleteAllAppliedResources() //
        .assertDeploymentDeleted();
  }

}

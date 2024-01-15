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

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion;
import org.eclipse.jkube.integrationtests.maven.MavenCase;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;

@KubernetesTest(createEphemeralNamespace = false)
abstract class Tomcat implements JKubeCase, MavenCase {

  static final String PROJECT_TOMCAT_JAKARTAEE = "projects-to-be-tested/maven/webapp/tomcat-jakartaee";
  static final String PROJECT_TOMCAT_JAKARTAEE_WITH_WEBAPPS_DIR = "projects-to-be-tested/maven/webapp/tomcat-jakartaee-with-webapps-dir";
  static final String PROJECT_TOMCAT_JAVAEE = "projects-to-be-tested/maven/webapp/tomcat-javaeelegacy";
  static final String PROJECT_TOMCAT_JAVAEE_WITH_WEBAPPS_DIR = "projects-to-be-tested/maven/webapp/tomcat-javaeelegacy-with-webapps-dir";
  static final String APPLICATION_NAME_TOMCAT_JAKARTAEE = "webapp-tomcat-jakartaee";
  static final String APPLICATION_NAME_TOMCAT_JAKARTAEE_WITH_WEBAPPS_DIR = "webapp-tomcat-jakartaee-with-webapps-dir";
  static final String APPLICATION_NAME_TOMCAT_JAVAEE = "webapp-tomcat-javaee-legacy";
  static final String APPLICATION_NAME_TOMCAT_JAVAEE_WITH_WEBAPPS_DIR = "webapp-tomcat-javaee-legacy-with-webapps-dir";

  private KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this).getKubernetesResource();
    assertPod(pod).apply(this).logContains("Catalina.start Server startup", 60);
    awaitService(this, pod.getMetadata().getNamespace()) //
      .assertPorts(hasSize(1)) //
      .assertPort("http", 8080, true) //
      .assertIsNodePort();
    return pod;
  }

  final void assertLogWithMigrationNotice(String log) {
    assertThat(log, stringContainsInOrder(
      "Performing migration from source [/usr/local/tomcat/webapps-javaee/ROOT.war] to destination",
      "Deploying web application archive [/usr/local/tomcat/webapps/ROOT.war]",
      "Deployment of web application archive [/usr/local/tomcat/webapps/ROOT.war] has finished",
      "org.apache.catalina.startup.Catalina.start Server startup in", "seconds"));
  }

  final void assertLogWithoutMigrationNotice(String log) {
    assertThat(log, stringContainsInOrder(
      "Deploying web application archive [/usr/local/tomcat/webapps/ROOT.war]",
      "Deployment of web application archive [/usr/local/tomcat/webapps/ROOT.war] has finished",
      "org.apache.catalina.startup.Catalina.start Server startup in", "seconds"));
    assertThat(log, not(containsString(
      "Performing migration from source [/usr/local/tomcat/webapps-javaee/ROOT.war] to destination")));
  }

  final void awaitDeployment(String namespace, String containerImage) {
    DeploymentAssertion.awaitDeployment(this, namespace) //
      .assertReplicas(equalTo(1)) //
      .assertContainers(hasSize(1)) //
      .assertContainers(hasItems(allOf( //
        hasProperty("image", equalTo(containerImage)), //
        hasProperty("name", equalTo("webapp")), //
        hasProperty("ports", hasSize(1)), //
        hasProperty("ports", hasItems(allOf( //
          hasProperty("name", equalTo("http")), //
          hasProperty("containerPort", equalTo(8080)) //
        ))) //
      )));
  }
}

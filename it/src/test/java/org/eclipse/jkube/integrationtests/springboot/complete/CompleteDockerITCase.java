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

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Locks.SPRINGBOOT_COMPLETE_K8s;
import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentAssertion.awaitDeployment;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
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
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
class CompleteDockerITCase extends Complete {

  private static final String DOCKER_ASSEMBLY_PROFILE = "Docker-Assembly";

  private KubernetesClient k;

  @BeforeEach
  void setUp() {
    k = new DefaultKubernetesClient();
  }

  @AfterEach
  void tearDown() {
    k.close();
    k = null;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return k;
  }

  @Override
  public String getApplication() {
    return "docker-spring-boot-complete";
  }

  @Override
  protected List<String> getProfiles() {
    return Collections.singletonList(DOCKER_ASSEMBLY_PROFILE);
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image and assembly files")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertInvocation(invocationResult);
    assertImageWasRecentlyBuilt("integration-tests", getApplication());
    assertImageWasRecentlyBuilt("integration-tests", getApplication(), "1337");
    final File dockerDirectory = new File(
      String.format("../%s/target/docker/integration-tests/docker-spring-boot-complete", getProject()));
    assertThat(dockerDirectory.exists(), equalTo(true));
    assertThat(new File(dockerDirectory, "tmp/docker-build.tar.gz").exists(), equalTo(true));
    final String dockerFileContent = String.join("\n",
      Files.readAllLines(new File(dockerDirectory, "build/Dockerfile").toPath()));
    assertThat(dockerFileContent, containsString("FROM adoptopenjdk/openjdk11:alpine-slim"));
    assertThat(dockerFileContent, containsString("ENV JAVA_APP_DIR=/deployments"));
    assertThat(dockerFileContent, containsString("LABEL some.label=\"The value\""));
    assertThat(dockerFileContent, containsString("EXPOSE 8082 8778 9779"));
    assertThat(dockerFileContent, matchesPattern(Pattern.compile("[\\s\\S]*COPY [^\\s]*? /deployments/\n" +
      "[\\s\\S]*")));
    assertThat(dockerFileContent, containsString("ENTRYPOINT [\"java\",\"-jar\",\"/deployments/spring-boot-complete-0.0.0-SNAPSHOT.jar\"]"));
    assertThat(dockerFileContent, containsString("USER 1000"));
    final List<String> imageFiles = listImageFiles(String.format("%s/%s", "integration-tests", getApplication()),
      "/deployments");
    assertThat(imageFiles, hasItem("/deployments/assembly-test/inlined-file.txt"));
    assertThat(imageFiles, not(hasItem("/deployments/assembly-test/not-considered.txt")));
    assertThat(imageFiles, not(hasItem("/deployments/static/ignored-file.txt")));
    assertThat(imageFiles, hasItem("/deployments/static/static-file.txt"));
    assertThat(imageFiles, not(hasItem("/deployments/will-be-included-if-no-assemblies-defined.txt")));
    assertThat(imageFiles, hasItem("/deployments/spring-boot-complete-0.0.0-SNAPSHOT.jar"));
    assertThat(new File(dockerDirectory, "build/Dockerfile").exists(), equalTo(true));
  }

  @Test
  @Order(2)
  @ResourceLock(value = SPRINGBOOT_COMPLETE_K8s, mode = READ_WRITE)
  @DisplayName("k8s:resource, should create manifests in specific directory")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube-docker/kubernetes.yml"));
    assertThat(new File(metaInfDirectory, "jkube-docker/kubernetes/password-secret.yml").exists(), equalTo(false));
    assertThat(new File(metaInfDirectory, "jkube-docker/kubernetes/docker-spring-boot-complete-deployment.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube-docker/kubernetes/docker-spring-boot-complete-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("k8s:apply, should deploy pod and service form manifests in specific directory")
  void k8sApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertInvocation(invocationResult);
    final String namespace = assertThatShouldApplyResources()
      .assertNodePortResponse("us-cli",
        containsString("This file was added via an assembly to the application thanks to the Awesomic JKube!"),
        "jkube", "static-file")
      .getKubernetesResource().getMetadata().getNamespace();
    awaitDeployment(this, namespace)
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", equalTo("integration-tests/docker-spring-boot-complete:latest")),
        hasProperty("name", equalTo("integration-tests-spring-boot-complete")),
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
    // Given
    final Properties properties = new Properties();
    properties.put("jkube.kubernetesManifest", "${basedir}/target/classes/META-INF/jkube-docker/kubernetes.yml");
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy", properties);
    // Then
    assertInvocation(invocationResult);
    assertThatShouldDeleteAllAppliedResources(this);
    assertDeploymentDeleted(this);
  }
}

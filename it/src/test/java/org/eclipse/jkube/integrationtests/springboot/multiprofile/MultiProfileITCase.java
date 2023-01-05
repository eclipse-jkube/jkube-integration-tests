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
package org.eclipse.jkube.integrationtests.springboot.multiprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;

class MultiProfileITCase implements MavenCase {

  private static final String PROJECT_MULTI_PROFILE = "projects-to-be-tested/maven/spring/multi-profile";

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper(new YAMLFactory());
  }

  @Override
  public String getProject() {
    return PROJECT_MULTI_PROFILE;
  }

  @Test
  @Tag(KUBERNETES)
  @DisplayName("k8s:resource, no specified profile, should create default resource manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertInvocation(invocationResult);
    assertPort("jkube/kubernetes.yml", 8081);
  }

  @Test
  @Tag(OPEN_SHIFT)
  @DisplayName("oc:resource, no specified default profile, should create default resource manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertInvocation(invocationResult);
    assertPort("jkube/openshift.yml", 8081);
  }

  @Test
  @Tag(KUBERNETES)
  @DisplayName("k8s:resource, production profile, should create production resource manifests")
  void k8sResourceProductionProfile() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource", "Production");
    // Then
    assertInvocation(invocationResult);
    assertPort("jkube/kubernetes.yml", 8080);
  }

  @Test
  @Tag(OPEN_SHIFT)
  @DisplayName("oc:resource, production profile, should create production resource manifests")
  void ocResourceProductionProfile() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource", "Production");
    // Then
    assertInvocation(invocationResult);
    assertPort("jkube/openshift.yml", 8080);
  }

  @SuppressWarnings("unchecked")
  private void assertPort(String yamlFile, int port) throws Exception {
    final File metaInfDirectory = new File(
        String.format("../%s/target/classes/META-INF", PROJECT_MULTI_PROFILE));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    final Map<String, ?> kubernetesYaml = objectMapper
        .readValue(new File(metaInfDirectory, yamlFile), Map.class);
    assertThat(kubernetesYaml, hasKey("items"));
    assertThat((List<Map<String, String>>) kubernetesYaml.get("items"), hasItem(hasEntry("kind", "Service")));
    final Optional<Integer> portEntry = (Optional<Integer>)((List<Map<String, ?>>) kubernetesYaml.get("items")).stream()
        .filter(p -> p.get("kind").equals("Service")).findFirst()
        .map(s -> (Map<String, ?>)s.get("spec"))
        .map(s -> (List<Map<String, ?>>)s.get("ports"))
        .map(ports -> ports.iterator().next())
        .map(p -> p.get("port"));
    assertThat(portEntry.isPresent(), equalTo(true));
    assertThat(portEntry.get(), equalTo(port));
  }

  private InvocationResult maven(String goal, String... profiles)
      throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties(), i -> i.setProfiles(Stream.of(profiles).collect(Collectors.toList())));
  }
}

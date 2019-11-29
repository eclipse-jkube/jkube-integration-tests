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
package org.eclipse.jkube.integrationtests.generators.springboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;

class MultiProfileITCase {

  private static final String PROJECT_MULTI_PROFILE = "projects-to-be-tested/generators/spring-boot/multi-profile";

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper(new YAMLFactory());
  }

  @Test
  @Tag(KUBERNETES)
  void k8sResource_defaultProfile_shouldCreateDefaultResources() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertPort("jkube/kubernetes.yml", 8081);
  }

  @Test
  @Tag(OPEN_SHIFT)
  void ocResource_defaultProfile_shouldCreateDefaultResources() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertPort("jkube/openshift.yml", 8081);
  }

  @Test
  @Tag(KUBERNETES)
  void k8sResource_productionProfile_shouldCreateProductionResources() throws Exception{
    // When
    final InvocationResult invocationResult = maven("k8s:resource", "Production");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertPort("jkube/kubernetes.yml", 8080);
  }

  @Test
  @Tag(OPEN_SHIFT)
  void ocResource_productionProfile_shouldCreateProductionResources() throws Exception{
    // When
    final InvocationResult invocationResult = maven("oc:resource", "Production");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertPort("jkube/openshift.yml", 8080);
  }

  @SuppressWarnings({"unchecked", "OptionalGetWithoutIsPresent"})
  private void assertPort(String yamlFile, int port) throws Exception {
    final File metaInfDirectory = new File(
        String.format("../%s/target/classes/META-INF", PROJECT_MULTI_PROFILE));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    final Map<String, ?> kubernetesYaml = objectMapper
        .readValue(new File(metaInfDirectory, yamlFile), Map.class);
    assertThat(kubernetesYaml, hasKey("items"));
    assertThat((List<Map>) kubernetesYaml.get("items"), hasItem(hasEntry("kind", "Service")));
    final Optional<Integer> portEntry = (Optional<Integer>)((List<Map>) kubernetesYaml.get("items")).stream()
        .filter(p -> p.get("kind").equals("Service")).findFirst()
        .map(s -> (Map<String, ?>)s.get("spec"))
        .map(s -> (List<Map<String, ?>>)s.get("ports"))
        .map(ports -> ports.iterator().next())
        .map(p -> p.get("port"));
    assertThat(portEntry.isPresent(), equalTo(true));
    assertThat(portEntry.get(), equalTo(port));
  }

  private static InvocationResult maven(String goal, String... profiles)
      throws IOException, InterruptedException, MavenInvocationException {

    return MavenUtils.execute(i -> {
      i.setBaseDirectory(new File("../"));
      i.setProjects(Collections.singletonList(PROJECT_MULTI_PROFILE));
      i.setGoals(Collections.singletonList(goal));
      i.setProfiles(Stream.of(profiles).collect(Collectors.toList()));
    });
  }
}

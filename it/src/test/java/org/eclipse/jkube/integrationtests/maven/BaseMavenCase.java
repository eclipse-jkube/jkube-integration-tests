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
package org.eclipse.jkube.integrationtests.maven;

import io.fabric8.kubernetes.api.model.Pod;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.assertServiceExists;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public abstract class BaseMavenCase implements MavenProject {

  private static final int MAX_RETRIES = 10;

  protected List<String> getProfiles() {
    return new ArrayList<>();
  }

  protected static void assertThatShouldDeleteAllAppliedResources(JKubeCase jKubeCase) throws InterruptedException {
    final Optional<Pod> matchingPod = jKubeCase.getKubernetesClient().pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith(jKubeCase.getApplication()))
      .filter(((Predicate<Pod>)(p -> p.getMetadata().getName().endsWith("-build"))).negate())
      .findAny();
    final Function<Pod, Pod> refreshPod = pod ->
      jKubeCase.getKubernetesClient().pods().withName(pod.getMetadata().getName()).fromServer().get();
    int current = 0;
    while (current++ < MAX_RETRIES) {
      Thread.sleep(300L);
      final boolean podIsStillRunning = matchingPod.map(refreshPod)
        .filter(updatedPod -> updatedPod.getMetadata().getDeletionTimestamp() != null).isPresent();
      if (podIsStillRunning && ++current > MAX_RETRIES) {
        throw new AssertionError(String.format(
          "Pod %s is still running when it should have been deleted", matchingPod.get().getMetadata().getName()));
      }
    }
    assertServiceExists(jKubeCase, equalTo(false));
  }

  protected static void assertListResource(File file) {
    assertThat(file, yaml(allOf(
      not(anEmptyMap()),
      hasEntry("kind", "List"),
      hasEntry("apiVersion", "v1")
    )));
  }

  protected Properties properties(Map<String, String> propertyMap) {
    final Properties ret = new Properties();
    ret.putAll(propertyMap);
    return ret;
  }

  protected Properties properties(String key, String value) {
    return properties(Collections.singletonMap(key, value));
  }

  protected MavenInvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  protected MavenInvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, properties, null);
  }

  protected final MavenInvocationResult maven(
    String goal, Properties properties, MavenUtils.InvocationRequestCustomizer chainedCustomizer)
    throws IOException, InterruptedException, MavenInvocationException {

    try (
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final PrintStream printStream = new PrintStream(baos)
    ) {
      final MavenUtils.InvocationRequestCustomizer recordStdOutCustomizer = invocationRequest ->
        invocationRequest.setOutputHandler(new PrintStreamHandler(printStream, true));
      final InvocationResult mavenResult = MavenUtils.execute(i -> {
        i.setBaseDirectory(new File("../"));
        i.setProjects(Collections.singletonList(getProject()));
        i.setGoals(Collections.singletonList(goal));
        i.setProfiles(getProfiles());
        i.setProperties(properties);
        recordStdOutCustomizer.customize(i);
        Optional.ofNullable(chainedCustomizer).ifPresent(cc -> cc.customize(i));
      });
      baos.flush();
      return new MavenInvocationResult(mavenResult, baos.toString(StandardCharsets.UTF_8));
    }
  }
}

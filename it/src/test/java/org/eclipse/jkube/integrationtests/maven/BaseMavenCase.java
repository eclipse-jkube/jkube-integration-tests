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
import java.util.function.Supplier;

import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

public abstract class BaseMavenCase implements MavenProject {

  private static final int MAX_RETRIES = 10;

  protected List<String> getProfiles() {
    return new ArrayList<>();
  }

  protected static void assertThatShouldDeleteAllAppliedResources(JKubeCase jKubeCase) throws InterruptedException {
    assertPodDeleted(jKubeCase);
    assertServiceDeleted(jKubeCase);
  }

  private static void assertPodDeleted(JKubeCase jKubeCase) throws InterruptedException {
    final Optional<Pod> matchingPod = jKubeCase.getKubernetesClient().pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith(jKubeCase.getApplication()))
      .filter(p -> p.getMetadata().getLabels().getOrDefault("app", "").equals(jKubeCase.getApplication()))
      .filter(Predicate.not(p -> p.getMetadata().getName().endsWith("-build")))
      .findAny();
    final Function<Pod, Pod> refreshPod = pod ->
      jKubeCase.getKubernetesClient().pods().withName(pod.getMetadata().getName()).fromServer().get();
    final boolean podIsStillRunning = retryWhileTrue(() -> matchingPod.map(refreshPod)
      .filter(updatedPod -> updatedPod.getMetadata().getDeletionTimestamp() == null)
      .isPresent()
    );
    assertThat("Pod is still running when it should have been deleted",
      podIsStillRunning, equalTo(false));
  }

  private static void assertServiceDeleted(JKubeCase jKubeCase) throws InterruptedException {
    final boolean servicesExists = retryWhileTrue(() -> jKubeCase.getKubernetesClient().services()
      .list().getItems().stream()
      .filter(s -> s.getMetadata().getDeletionTimestamp() == null)
      .anyMatch(s -> s.getMetadata().getName().equals(jKubeCase.getApplication()))
    );
    assertThat("Service is still present when it should have been deleted",
      servicesExists, equalTo(false));
  }

  protected static void assertDeploymentDeleted(JKubeCase jKubeCase) throws InterruptedException {
    final boolean deploymentExists = retryWhileTrue(() -> jKubeCase.getKubernetesClient().apps().deployments()
      .list().getItems().stream()
      .filter(s -> s.getMetadata().getDeletionTimestamp() == null)
      .anyMatch(s -> s.getMetadata().getName().equals(jKubeCase.getApplication()))
    );
    assertThat("Deployment is still present when it should have been deleted",
      deploymentExists, equalTo(false));
  }

  private static boolean retryWhileTrue(Supplier<Boolean> func) throws InterruptedException {
    boolean ret = true;
    int current = 0;
    while (current++ < MAX_RETRIES) {
      ret = func.get();
      if (!ret) {
        break;
      }
      Thread.sleep(300L);
    }
    return ret;
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
      printStream.flush();
      baos.flush();
      return new MavenInvocationResult(mavenResult, baos.toString(StandardCharsets.UTF_8));
    }
  }
}

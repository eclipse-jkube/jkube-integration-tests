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
package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.eclipse.jkube.integrationtests.AsyncUtil.await;
import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertLabels;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class PodAssertion extends KubernetesClientAssertion<Pod> {

  private PodAssertion(JKubeCase jKubeCase, Pod pod) {
    super(jKubeCase, pod);
  }

  public static Function<JKubeCase, PodAssertion> assertPod(Pod pod) {
    return jKubeCase -> new PodAssertion(jKubeCase, pod);
  }

  public static PodAssertion awaitPod(JKubeCase jKubeCase) throws Exception {
    final var pod = awaitPod(jKubeCase.getKubernetesClient(), jKubeCase.getApplication());
    assertThat(pod, notNullValue());
    assertThat(pod.getMetadata().getName(), startsWith(jKubeCase.getApplication()));
    assertLabels(jKubeCase).assertStandardLabels(pod.getMetadata()::getLabels);
    return assertPod(pod).apply(jKubeCase);
  }

  public PodAssertion logContains(CharSequence sequence, long timeoutSeconds) throws InterruptedException {
    final AtomicReference<String> lastLog = new AtomicReference<>("");
    try {
      await(() -> lastLog.updateAndGet(old -> podResource().getLog()))
        .apply(l -> l.contains(sequence))
        .get(timeoutSeconds, TimeUnit.SECONDS);
      return this;
    } catch (ExecutionException | TimeoutException ignore) {
      // NO OP
    }
    throw new AssertionError(String.format("Error awaiting for log to contain:%n %s%nBut was:%n%s",
      sequence, lastLog.get()));
  }

  private PodResource podResource() {
    return getKubernetesClient().pods()
      .inNamespace(getKubernetesResource().getMetadata().getNamespace())
      .withName(getKubernetesResource().getMetadata().getName());
  }

  private static Pod awaitPod(KubernetesClient kc,String appId) throws Exception {
    try {
      // Wait for Pod to be created
      kc.pods().withLabel("app", appId)
        .informOnCondition(p -> !p.isEmpty())
        .get(DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
      // Wait for Pod to be ready
      kc.pods().withLabel("app", appId)
        .informOnCondition(p -> p.stream().anyMatch(Readiness::isPodReady))
        .get(DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException ex) {
      // NO OP
    }
    final var pod = kc.pods().withLabel("app", appId).list().getItems().stream().findFirst().orElse(null);
    if (pod != null && Readiness.isPodReady(pod)) {
      return pod;
    }
    throw new AssertionError(String.format("Error awaiting Pod '%s'\n%s",
      appId, pod != null ? Serialization.asYaml(pod) : "No Pod found"));
  }
}

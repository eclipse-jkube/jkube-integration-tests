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
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
    Pod pod = awaitPod(jKubeCase.getKubernetesClient(), jKubeCase.getApplication());
    if (pod == null) {
      printDiagnosis(jKubeCase);
      pod = retryDeployment(jKubeCase);
    }
    assertThat(pod, notNullValue());
    assertThat(pod.getMetadata().getName(), startsWith(jKubeCase.getApplication()));
    assertLabels(jKubeCase).assertStandardLabels(pod.getMetadata()::getLabels);
    return assertPod(pod).apply(jKubeCase);
  }

  public PodAssertion logContains(CharSequence sequence, long timeoutSeconds) throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<String> lastLog = new AtomicReference<>("");
    final ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
    final Runnable waitCondition = () -> {
      lastLog.set(podResource().getLog());
      if (lastLog.get().contains(sequence)) {
        latch.countDown();
      }
    };
    @SuppressWarnings("rawtypes")
    final Future task = es.scheduleAtFixedRate(waitCondition, 0L, 500L, TimeUnit.MILLISECONDS);
    final boolean logContainsSequence = latch.await(timeoutSeconds, TimeUnit.SECONDS);
    task.cancel(true);
    es.shutdown();
    if (!logContainsSequence) {
      throw new AssertionError(String.format("Error awaiting for log to contain:%n %s%nBut was:%n%s",
        sequence, lastLog.get()));
    }
    return this;
  }

  private PodResource<Pod> podResource() {
    return getKubernetesClient().pods()
      .inNamespace(getKubernetesResource().getMetadata().getNamespace())
      .withName(getKubernetesResource().getMetadata().getName());
  }

  private static Pod awaitPod(KubernetesClient kc,String appId) throws Exception {
    return kc.pods().withLabel("app", appId)
      .informOnCondition(p -> p.stream().anyMatch(Readiness::isPodReady))
      .get(DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS).stream()
      .filter(Readiness::isPodReady)
      .findFirst()
      .orElse(null);
  }

  private static Pod retryDeployment(JKubeCase jKubeCase) throws Exception {
    if (jKubeCase.getKubernetesClient().isAdaptable(OpenShiftClient.class)) {
      System.err.println("\n\n===========================\nDeleting unusable PODs");
      jKubeCase.getKubernetesClient().pods().list().getItems().stream()
        .filter(pod-> pod.getMetadata().getName().startsWith(jKubeCase.getApplication()))
        .forEach(pod ->  {
          System.err.printf("\nDeleting POD %s\n", pod.getMetadata().getName());
          jKubeCase.getKubernetesClient().resource(pod).delete();
        });
      final OpenShiftClient oc = jKubeCase.getKubernetesClient().adapt(OpenShiftClient.class);
      System.err.println("\n\n===========================\nRedeploying!");
      oc.deploymentConfigs().withName(jKubeCase.getApplication()).edit(dc ->
        new DeploymentConfigBuilder(dc).editSpec().editStrategy()
          .withType("Recreate")
          .endStrategy().endSpec().build());
      oc.deploymentConfigs().withName(jKubeCase.getApplication()).deployLatest();
      final Pod retriedPod = awaitPod(jKubeCase.getKubernetesClient(), jKubeCase.getApplication());
      printDiagnosis(jKubeCase);
      return retriedPod;
    }
    return null;
  }
}

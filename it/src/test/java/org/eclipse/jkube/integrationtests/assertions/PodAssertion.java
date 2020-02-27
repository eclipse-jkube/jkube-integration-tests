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

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.openshift.client.OpenShiftClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.PodReadyWatcher;

import java.util.concurrent.TimeUnit;
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

  public static PodAssertion awaitPod(JKubeCase jKubeCase) throws InterruptedException {
    final PodReadyWatcher podWatcher = new PodReadyWatcher();
    jKubeCase.getKubernetesClient().pods().withLabel("app", jKubeCase.getApplication()).watch(podWatcher);
    Pod pod = awaitPod(jKubeCase.getKubernetesClient(), jKubeCase.getApplication());
    if (pod == null && jKubeCase.getKubernetesClient().isAdaptable(OpenShiftClient.class)) {
      retryDeployment(jKubeCase.getKubernetesClient().adapt(OpenShiftClient.class), jKubeCase.getApplication());
      pod  = awaitPod(jKubeCase.getKubernetesClient(), jKubeCase.getApplication());
    }
    assertThat(pod, notNullValue());
    assertThat(pod.getMetadata().getName(), startsWith(jKubeCase.getApplication()));
    assertLabels(jKubeCase).assertStandardLabels(pod.getMetadata()::getLabels);
    return assertPod(pod).apply(jKubeCase);
  }

  public PodAssertion logContains(CharSequence sequence, long timeoutSeconds) throws InterruptedException {
    podResource().waitUntilCondition(conditionPod -> {
        try {
          return podResource().getLog().contains(sequence);
        } catch (Exception ex) {
          // Ignore error and iterate again
        }
        return false;
      },
      timeoutSeconds, TimeUnit.SECONDS);
    return this;
  }

  private PodResource<Pod, DoneablePod> podResource() {
    return getKubernetesClient().pods()
      .inNamespace(getKubernetesResource().getMetadata().getNamespace())
      .withName(getKubernetesResource().getMetadata().getName());
  }

  private static Pod awaitPod(KubernetesClient kc,String appId) throws InterruptedException {
    final PodReadyWatcher podWatcher = new PodReadyWatcher();
    kc.pods().withLabel("app", appId).watch(podWatcher);
    return podWatcher.await(DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
  }

  private static void retryDeployment(OpenShiftClient oc, String deploymentConfigName) {
    System.err.println("\nStatus:");
    System.err.println(oc.deploymentConfigs().withName(deploymentConfigName).get().getStatus());
    System.err.println("\nStatus Details:");
    System.err.println(oc.deploymentConfigs().withName(deploymentConfigName).get().getStatus().getDetails());
    System.err.println("\nStatus Conditions:");
    oc.deploymentConfigs().withName(deploymentConfigName).get().getStatus().getConditions().forEach(
      condition -> System.err.println(condition.toString())
    );
    oc.deploymentConfigs().withName(deploymentConfigName).deployLatest();
  }
}

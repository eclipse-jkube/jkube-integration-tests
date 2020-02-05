package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;

import java.util.concurrent.TimeUnit;

public class PodAssertion {

  private final KubernetesClient kubernetesClient;
  private final Pod pod;

  private PodAssertion(KubernetesClient kubernetesClient, Pod pod) {
    this.kubernetesClient = kubernetesClient;
    this.pod = pod;
  }

  public static PodAssertion assertPod(KubernetesClient kubernetesClient, Pod pod) {
    return new PodAssertion(kubernetesClient, pod);
  }

  public void logContains(CharSequence sequence, long timeoutSeconds) throws InterruptedException {
    podResource().waitUntilCondition(conditionPod -> {
        try {
          return podResource().getLog().contains(sequence);
        } catch (Exception ex) {
          // Ignore error and iterate again
        }
        return false;
      },
      timeoutSeconds, TimeUnit.SECONDS);
  }

  private PodResource<Pod, DoneablePod> podResource() {
    return kubernetesClient.pods()
      .inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName());
  }
}

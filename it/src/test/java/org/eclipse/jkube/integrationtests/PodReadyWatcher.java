package org.eclipse.jkube.integrationtests;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PodReadyWatcher implements Watcher<Pod> {

  private final CountDownLatch podAvailableSignal = new CountDownLatch(1);
  private final AtomicReference<Pod> podReference = new AtomicReference<>();

  @Override
  public void eventReceived(Action action, Pod pod) {
    Optional.ofNullable(pod)
      .filter(Readiness::isPodReady)
      .ifPresent(p -> {
        podReference.set(p);
        podAvailableSignal.countDown();
      });
  }

  @Override
  public void onClose(KubernetesClientException e) {

  }

  public Pod await(long timeout, TimeUnit unit) throws InterruptedException {
    podAvailableSignal.await(timeout, unit);
    return podReference.get();
  }
}

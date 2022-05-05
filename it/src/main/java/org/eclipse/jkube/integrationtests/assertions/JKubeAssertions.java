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
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class JKubeAssertions {

  private static final int DEFAULT_TIMEOUT_SECONDS = 10;
  private static final int MAX_RETRIES = 10;

  private final JKubeCase jKubeCase;

  JKubeAssertions(JKubeCase jKubeCase) {
    this.jKubeCase = jKubeCase;
  }

  public static JKubeAssertions assertJKube(JKubeCase jKubeCase) {
    return new JKubeAssertions(jKubeCase);
  }

  public JKubeAssertions assertPodDeleted() throws InterruptedException {
    final var matchingPod = jKubeCase.getKubernetesClient().pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith(jKubeCase.getApplication()))
      .filter(p -> p.getMetadata().getLabels().getOrDefault("app", "").equals(jKubeCase.getApplication()))
      .filter(Predicate.not(p -> p.getMetadata().getName().endsWith("-build")))
      .findAny();
    final UnaryOperator<Pod> refreshPod = pod ->
      jKubeCase.getKubernetesClient().pods().withName(pod.getMetadata().getName()).fromServer().get();
    final boolean podIsStillRunning = retryWhileTrue(() -> matchingPod.map(refreshPod)
      .filter(updatedPod -> updatedPod.getMetadata().getDeletionTimestamp() == null)
      .isPresent()
    );
    assertThat("Pod is still running when it should have been deleted",
      podIsStillRunning, equalTo(false));
    return this;
  }

  public JKubeAssertions assertServiceDeleted() {
    try {
      jKubeCase.getKubernetesClient().services().withName(jKubeCase.getApplication())
        .waitUntilCondition(d -> d == null || d.getMetadata().getDeletionTimestamp() != null, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new AssertionError("Deployment is still present when it should have been deleted", e);
    }
    return this;
  }

  public JKubeAssertions assertDeploymentDeleted() {
    try {
      jKubeCase.getKubernetesClient().apps().deployments().withName(jKubeCase.getApplication())
        .waitUntilCondition(d -> d == null || d.getMetadata().getDeletionTimestamp() != null, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new AssertionError("Deployment is still present when it should have been deleted", e);
    }
    return this;
  }

  public JKubeAssertions assertThatShouldDeleteAllAppliedResources() throws InterruptedException {
    return assertPodDeleted().assertServiceDeleted();
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
}

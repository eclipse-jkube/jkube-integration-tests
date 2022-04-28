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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.hamcrest.Matcher;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertLabels;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public class DeploymentAssertion extends KubernetesClientAssertion<Deployment> {

  private DeploymentAssertion(JKubeCase jKubeCase, Deployment deployment) {
    super(jKubeCase, deployment);
  }

  public static Function<JKubeCase, DeploymentAssertion> assertDeployment(Deployment deployment) {
    return jKubeCase -> new DeploymentAssertion(jKubeCase, deployment);
  }

  public static void assertDeploymentExists(JKubeCase jKubeCase, Matcher<Boolean> existsMatcher) {
    final boolean deploymentExists = jKubeCase.getKubernetesClient().apps().deployments().list().getItems().stream()
      .anyMatch(s -> s.getMetadata().getName().startsWith(jKubeCase.getApplication()));
    assertThat(deploymentExists, existsMatcher);
  }

  public static DeploymentAssertion awaitDeployment(JKubeCase jKubeCase, String namespace) {
    final Deployment deployment = jKubeCase.getKubernetesClient().apps().deployments()
      .inNamespace(namespace)
      .withName(jKubeCase.getApplication())
      .waitUntilCondition(Objects::nonNull, DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
    assertThat(deployment, notNullValue());
    assertLabels(jKubeCase)
      .assertStandardLabels(deployment.getMetadata()::getLabels)
      .assertStandardLabels(deployment.getSpec().getSelector()::getMatchLabels)
      .assertStandardLabels(deployment.getSpec().getTemplate().getMetadata()::getLabels);
    return assertDeployment(deployment).apply(jKubeCase);
  }

  public DeploymentAssertion assertReplicas(Matcher<Number> replicasMatcher) {
    assertThat(getKubernetesResource().getSpec().getReplicas(), replicasMatcher);
    return this;
  }

  public DeploymentAssertion assertContainers(Matcher<? super Collection<Container>> containersMatcher) {
    assertThat(getKubernetesResource().getSpec().getTemplate().getSpec().getContainers(), containersMatcher);
    return this;
  }
}

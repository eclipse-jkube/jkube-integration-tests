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
import io.fabric8.openshift.api.model.DeploymentConfig;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.hamcrest.Matcher;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertLabels;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public class DeploymentConfigAssertion  extends KubernetesClientAssertion<DeploymentConfig> {

  private DeploymentConfigAssertion(JKubeCase jKubeCase, DeploymentConfig deploymentConfig) {
    super(jKubeCase, deploymentConfig);
  }

  public static Function<JKubeCase, DeploymentConfigAssertion> assertDeploymentConfig(DeploymentConfig deploymentConfig) {
    return jKubeCase -> new DeploymentConfigAssertion(jKubeCase, deploymentConfig);
  }

  public static DeploymentConfigAssertion awaitDeploymentConfig(JKubeCase jKubeCase, String namespace) {
    final DeploymentConfig deploymentConfig = jKubeCase.getOpenShiftClient().deploymentConfigs()
      .inNamespace(namespace)
      .withName(jKubeCase.getApplication())
      .waitUntilCondition(Objects::nonNull, DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
    assertThat(deploymentConfig, notNullValue());
    assertLabels(jKubeCase)
      .assertStandardLabels(deploymentConfig.getMetadata()::getLabels)
      .assertStandardLabels(deploymentConfig.getSpec()::getSelector)
      .assertStandardLabels(deploymentConfig.getSpec().getTemplate().getMetadata()::getLabels);
    return assertDeploymentConfig(deploymentConfig).apply(jKubeCase);
  }

  public DeploymentConfigAssertion assertReplicas(Matcher<Number> replicasMatcher) {
    assertThat(getKubernetesResource().getSpec().getReplicas(), replicasMatcher);
    return this;
  }

  public DeploymentConfigAssertion assertContainers(Matcher<? super Collection<Container>> containersMatcher) {
    assertThat(getKubernetesResource().getSpec().getTemplate().getSpec().getContainers(), containersMatcher);
    return this;
  }
}

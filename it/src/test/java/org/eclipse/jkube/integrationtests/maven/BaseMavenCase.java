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
import org.eclipse.jkube.integrationtests.JKubeCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.assertServiceExists;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public abstract class BaseMavenCase implements MavenProject {

  protected List<String> getProfiles() {
    return new ArrayList<>();
  }

  protected static void assertThatShouldDeleteAllAppliedResources(JKubeCase jKubeCase) {
    final Optional<Pod> matchingPod = jKubeCase.getKubernetesClient().pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith(jKubeCase.getApplication()))
      .filter(((Predicate<Pod>)(p -> p.getMetadata().getName().endsWith("-build"))).negate())
      .findAny();
    final Function<Pod, Pod> refreshPod = pod ->
      jKubeCase.getKubernetesClient().pods().withName(pod.getMetadata().getName()).get();
    matchingPod.map(refreshPod).ifPresent(updatedPod ->
      assertThat(updatedPod.getMetadata().getDeletionTimestamp(), notNullValue()));
    assertServiceExists(jKubeCase, equalTo(false));
  }

  protected InvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  protected final InvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, properties, null);
  }

  protected final InvocationResult maven(String goal, Properties properties, MavenUtils.InvocationRequestCustomizer chainedCustomizer)
    throws IOException, InterruptedException, MavenInvocationException {

    return MavenUtils.execute(i -> {
      i.setBaseDirectory(new File("../"));
      i.setProjects(Collections.singletonList(getProject()));
      i.setGoals(Collections.singletonList(goal));
      i.setProfiles(getProfiles());
      i.setProperties(properties);
      Optional.ofNullable(chainedCustomizer).ifPresent(cc -> cc.customize(i));
    });
  }
}

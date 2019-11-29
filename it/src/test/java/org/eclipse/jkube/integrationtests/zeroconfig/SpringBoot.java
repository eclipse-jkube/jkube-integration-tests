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
package org.eclipse.jkube.integrationtests.zeroconfig;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

abstract class SpringBoot {

  static final String PROJECT_ZERO_CONFIG = "projects-to-be-tested/zero-config/spring-boot";

  final void assertThatShouldApplyResources(KubernetesClient kc) {
    final Optional<Pod> pod = kc.pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith("zero-config-spring-boot"))
      .filter(s -> s.getMetadata().getLabels().containsKey("app"))
      .filter(s -> s.getMetadata().getLabels().get("app").equals("zero-config-spring-boot"))
      .findFirst();
    assertThat(pod.isPresent(), equalTo(true));
    assertThat(pod.get().getMetadata().getLabels(), hasEntry("provider", "jkube"));
    final Optional<Service> service = kc.services().list().getItems().stream()
      .filter(s -> s.getMetadata().getName().startsWith("zero-config-spring-boot"))
      .findFirst();
    assertThat(service.isPresent(), equalTo(true));
    assertThat(service.get().getMetadata().getLabels(), hasEntry("expose", "true"));
    assertThat(service.get().getMetadata().getLabels(), hasEntry("app", "zero-config-spring-boot"));
    assertThat(service.get().getMetadata().getLabels(), hasEntry("provider", "jkube"));
    assertThat(service.get().getMetadata().getLabels(), hasEntry("group", "org.eclipse.jkube.integration-tests"));
    assertThat(service.get().getSpec().getSelector(), hasEntry("app", "zero-config-spring-boot"));
    assertThat(service.get().getSpec().getSelector(), hasEntry("provider", "jkube"));
    assertThat(service.get().getSpec().getSelector(), hasEntry("group", "org.eclipse.jkube.integration-tests"));
    assertThat(service.get().getSpec().getPorts(), hasSize(1));
    final ServicePort servicePort = service.get().getSpec().getPorts().iterator().next() ;
    assertThat(servicePort.getName(), equalTo("http"));
    assertThat(servicePort.getPort(), equalTo(8080));
    assertThat(servicePort.getNodePort(), nullValue());
  }

  final void assertThatShouldDeleteAllAppliedResources(KubernetesClient kc) throws Exception {
    final Optional<Pod> matchingPod = kc.pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith("zero-config-spring-boot"))
      .findAny();
    if (matchingPod.isPresent()) {
      final ContainerStatus lastContainerStatus = matchingPod.get().getStatus()
        .getContainerStatuses().iterator().next();
      assertThat(lastContainerStatus.getState().getTerminated(), notNullValue());
    }
    final boolean servicesExist = kc.services().list().getItems().stream()
      .anyMatch(s -> s.getMetadata().getName().startsWith("zero-config-spring-boot"));
    assertThat(servicesExist, equalTo(false));
  }

  InvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  final InvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return MavenUtils.execute(i -> {
      i.setBaseDirectory(new File("../"));
      i.setProjects(Collections.singletonList(PROJECT_ZERO_CONFIG));
      i.setGoals(Collections.singletonList(goal));
      i.setProperties(properties);
    });
  }
}

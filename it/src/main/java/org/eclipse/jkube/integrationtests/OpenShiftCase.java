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
package org.eclipse.jkube.integrationtests;

import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.client.OpenShiftClient;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public interface OpenShiftCase extends JKubeCase {

  String S2I_BUILD_SUFFIX = "-s2i";
  String OPENSHIFT_BUILD_LABEL = "openshift.io/build.name";

  default OpenShiftClient getOpenShiftClient() {
    return getKubernetesClient().adapt(OpenShiftClient.class);
  }

  default void cleanUpCluster() {
    getOpenShiftClient().imageStreams().withName(getApplication()).delete();
    getOpenShiftClient().builds().withLabel("buildconfig", getApplication() + S2I_BUILD_SUFFIX).delete();
    getOpenShiftClient().buildConfigs().withName(getApplication() + S2I_BUILD_SUFFIX).delete();
    getOpenShiftClient().pods().withLabel(OPENSHIFT_BUILD_LABEL).list().getItems().stream()
      .filter(p -> p.getMetadata().getLabels().get(OPENSHIFT_BUILD_LABEL).startsWith(getApplication() + S2I_BUILD_SUFFIX))
      .forEach(p -> getOpenShiftClient().resource(p).delete());
  }

  default void assertOpenShiftDockerBuildCompletedWithLogs(String... logs) {
    List<BuildConfig> buildConfigDockerList = getOpenShiftClient().buildConfigs()
      .withLabel("app", getApplication())
      .list()
      .getItems().stream()
      .filter(bc -> bc.getSpec().getStrategy().getType().equals("Docker"))
      .sorted(Comparator.comparing(KubernetesResourceUtil::getAge))
      .collect(Collectors.toList());
    assertThat(buildConfigDockerList.size(), greaterThanOrEqualTo(1));
    List<Build> dockerBuild = getOpenShiftClient().builds()
      .withLabel("openshift.io/build-config.name", buildConfigDockerList.get(0).getMetadata().getName())
      .list()
      .getItems().stream()
      .filter(b -> b.getStatus().getPhase().equals("Complete"))
      .sorted(Comparator.comparing(KubernetesResourceUtil::getAge))
      .collect(Collectors.toList());
    assertThat(dockerBuild.size(), greaterThanOrEqualTo(1));
    String dockerBuildLog = getOpenShiftClient().builds().withName(dockerBuild.get(0).getMetadata().getName()).getLog();
    Arrays.stream(logs).forEach(l -> assertThat(dockerBuildLog, containsString(l)));
  }
}

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

public interface OpenShiftCase extends JKubeCase {

  String S2I_BUILD_SUFFIX = "-s2i";
  String OPENSHIFT_BUILD_LABEL = "openshift.io/build.name";

  default void cleanUpCluster() {
    getOpenShiftClient().imageStreams().withName(getApplication()).delete();
    getOpenShiftClient().builds().withLabel("buildconfig", getApplication() + S2I_BUILD_SUFFIX).delete();
    getOpenShiftClient().buildConfigs().withName(getApplication() + S2I_BUILD_SUFFIX).delete();
    getOpenShiftClient().pods().withLabel(OPENSHIFT_BUILD_LABEL).list().getItems().stream()
      .filter(p -> p.getMetadata().getLabels().get(OPENSHIFT_BUILD_LABEL).startsWith(getApplication() + S2I_BUILD_SUFFIX))
      .forEach(p -> getOpenShiftClient().resource(p).delete());
  }
}

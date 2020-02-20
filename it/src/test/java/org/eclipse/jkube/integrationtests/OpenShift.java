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

import io.fabric8.openshift.client.OpenShiftClient;

public class OpenShift {

  private static final String S2I_BUILD_SUFFIX = "-s2i";
  private static final String OPENSHIFT_BUILD_LABEL = "openshift.io/build.name";

  private OpenShift() {}

  public static void cleanUpCluster(OpenShiftClient oc, JKubeCase jKubeCase) {
    oc.imageStreams().withName(jKubeCase.getApplication()).delete();
    oc.builds().withLabel("buildconfig", jKubeCase.getApplication()+S2I_BUILD_SUFFIX).delete();
    oc.buildConfigs().withName(jKubeCase.getApplication()+S2I_BUILD_SUFFIX).delete();
    oc.pods().withLabel(OPENSHIFT_BUILD_LABEL).list().getItems().stream()
      .filter(p -> p.getMetadata().getLabels().get(OPENSHIFT_BUILD_LABEL).startsWith(jKubeCase.getApplication()+S2I_BUILD_SUFFIX))
      .forEach(p -> oc.resource(p).delete());
  }
}

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

import static org.eclipse.jkube.integrationtests.cli.CliUtils.runCommand;

public class Hacks {
  private Hacks() {
  }

  /**
   * Source (FROM) docker image is not pullable from OpenShift Build.
   *
   * This is caused by a bug in JKube when using Kuberentes mode with oc-maven-plugin.
   *
   * This hacks pulls the image using docker cli previously so that the build won't fail.
   * TODO: Remove once issue is fixed in JKube (NullPointerException: RegistryService#createAuthConfig)
   * <pre>
   *  Caused by: java.lang.NullPointerException
   *       at org.eclipse.jkube.kit.build.service.docker.RegistryService.createAuthConfig (RegistryService.java:153)
   *       at org.eclipse.jkube.kit.build.service.docker.RegistryService.pullImageWithPolicy (RegistryService.java:112)
   *       at org.eclipse.jkube.kit.build.service.docker.BuildService.autoPullBaseImage (BuildService.java:262)
   *       at org.eclipse.jkube.kit.build.service.docker.BuildService.buildImage (BuildService.java:74)
   *       at org.eclipse.jkube.kit.config.service.kubernetes.DockerBuildService.build (DockerBuildService.java:49)
   *       at org.eclipse.jkube.maven.plugin.mojo.build.AbstractDockerMojo.buildAndTag (AbstractDockerMojo.java:687)
   * </pre>
   */
  public static void hackToPreventNullPointerInRegistryServiceCreateAuthConfig(String fromImage) throws Exception {
    runCommand(String.format("docker pull %s", fromImage));
  }
}

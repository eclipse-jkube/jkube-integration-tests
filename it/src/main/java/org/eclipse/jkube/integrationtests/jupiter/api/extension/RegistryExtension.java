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
package org.eclipse.jkube.integrationtests.jupiter.api.extension;

import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.File;

import static org.eclipse.jkube.integrationtests.cli.CliUtils.isWindows;
import static org.hamcrest.MatcherAssert.assertThat;

public class RegistryExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Logger logger = LoggerFactory.getLogger(RegistryExtension.class);
  private static final ExtensionContext.Namespace EXT_NAMESPACE = ExtensionContext.Namespace
    .create(RegistryExtension.class);

  @Override
  public synchronized void beforeAll(ExtensionContext context) throws Exception {
    if (Boolean.FALSE.equals(getStore(context).getOrDefault("started", Boolean.class, false))) {
      getStore(context).put("started", true);
      final DockerRegistry annotation = context.getRequiredTestClass().getAnnotation(DockerRegistry.class);
      logger.debug(() -> "Starting Docker Registry Extension");
      final CliUtils.CliResult dockerRegistry;
      if (isWindows()) {
        dockerRegistry = startWindowsDockerRegistry(annotation);
      } else {
        dockerRegistry = startRegularDockerRegistry(annotation);
      }
      assertThat(dockerRegistry.getOutput(), dockerRegistry.getExitCode(), Matchers.equalTo(0));
      logger.debug(() -> "Docker Registry started successfully");
    }
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    logger.debug(() -> "Closing Docker Registry");
    final DockerRegistry annotation = context.getRequiredTestClass().getAnnotation(DockerRegistry.class);
    CliUtils.runCommand("docker rm -f " + annotation.containerName());
  }

  private static ExtensionContext.Store getStore(ExtensionContext context) {
    return context.getRoot().getStore(EXT_NAMESPACE);
  }

  private static CliUtils.CliResult startRegularDockerRegistry(DockerRegistry dockerRegistry) throws Exception {
    logger.debug(() -> "Starting standard Docker Registry");
    return CliUtils.runCommand("docker run -d -p 5000:5000 --name " +
      dockerRegistry.containerName() + " registry:2");
  }

  private static CliUtils.CliResult startWindowsDockerRegistry(DockerRegistry dockerRegistry) throws Exception {
    logger.debug(() -> "Starting Windows specific Docker Registry");
    final File registry = new File("C:\\registry");
    if (!registry.exists() && !registry.mkdirs()) {
      throw new IllegalStateException("Directory C:\\registry cannot be created");
    }
    return CliUtils.runCommand("docker run --rm -d -p 5000:5000 --name " +
      dockerRegistry.containerName() + " -v C:\\registry:C:\\registry marcnuri/docker-registry-windows:ltsc2022");
  }

}

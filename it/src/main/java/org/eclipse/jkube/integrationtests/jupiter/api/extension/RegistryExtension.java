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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.File;

import static org.eclipse.jkube.integrationtests.cli.CliUtils.isWindows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class RegistryExtension implements BeforeAllCallback, CloseableResource {

  private static final Logger logger = LoggerFactory.getLogger(RegistryExtension.class);
  private static boolean started = false;

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    if (!started) {
      logger.debug(() -> "Starting Docker Registry Extension");
      started = true;
      context.getRoot().getStore(GLOBAL).put("Docker Registry Callback Hook", this);
      final CliUtils.CliResult dockerRegistry;
      if (isWindows()) {
        dockerRegistry = startWindowsDockerRegistry();
      } else {
        dockerRegistry = startRegularDockerRegistry();
      }
      assertThat(dockerRegistry.getOutput(), dockerRegistry.getExitCode(), Matchers.equalTo(0));
      logger.debug(() -> "Docker Registry started successfully");
    }
  }

  @Override
  public void close() throws Exception {
    logger.debug(() -> "Closing Docker Registry");
    CliUtils.runCommand("docker rm -f registry");
  }

  private static CliUtils.CliResult startRegularDockerRegistry() throws Exception {
    logger.debug(() -> "Starting standard Docker Registry");
    return CliUtils.runCommand("docker run -d -p 5000:5000 --name registry registry:2");
  }

  private static CliUtils.CliResult startWindowsDockerRegistry() throws Exception {
    logger.debug(() -> "Starting Windows specific Docker Registry");
    final File registry = new File("C:\\registry");
    if (!registry.exists() && !registry.mkdirs()) {
      throw new IllegalStateException("Directory C:\\registry cannot be created");
    }
    return CliUtils.runCommand("docker run --rm -d -p 5000:5000 --name registry -v C:\\registry:C:\\registry marcnuri/docker-registry-windows:ltsc2022");
  }
}

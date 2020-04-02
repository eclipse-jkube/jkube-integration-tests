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
package org.eclipse.jkube.integrationtests.docker;

import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class RegistryExtension implements BeforeAllCallback, CloseableResource {

  private static boolean started = false;

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    if (!started) {
      started = true;
      context.getRoot().getStore(GLOBAL).put("Docker Registry Callback Hook", this);
      final CliUtils.CliResult dockerRegistry;
      if (isWindows()) {
        dockerRegistry = startWindowsDockerRegistry();
      } else {
        dockerRegistry = startRegularDockerRegistry();
      }
      assertThat(dockerRegistry.getExitCode(), Matchers.equalTo(0));

    }
  }

  @Override
  public void close() throws Exception {
    CliUtils.runCommand("docker rm -f registry");
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().startsWith("windows");
  }

  private static CliUtils.CliResult startRegularDockerRegistry() throws Exception {
    return CliUtils.runCommand("docker run -d -p 5000:5000 --name registry registry:2");
  }

  private static CliUtils.CliResult startWindowsDockerRegistry() throws Exception {
    if (!new File("C:\\registry").mkdirs()) {
      throw new IllegalStateException("Directory C:\\registry cannot be created");
    }
    return CliUtils.runCommand("docker run -d -p 5000:5000 --name registry -v C:\\registry:C:\\registry stefanscherer/registry-windows:2.6.2");
  }
}
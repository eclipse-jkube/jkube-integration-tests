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
package org.eclipse.jkube.integrationtests.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class CliUtils {

  private static final String PROP_OS_NAME = "os.name";

  private CliUtils() {
  }

  public static CliResult runCommand(String command) throws IOException, InterruptedException {
    final String[] processCommand;
    if (isWindows()) {
      processCommand = new String[]{"cmd", "/c", command};
    } else {
      processCommand = new String[]{"sh", "-c", command};
    }
    final Process process = new ProcessBuilder()
        .redirectErrorStream(true)
        .command(processCommand)
        .start();
    final Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8.name())
            .useDelimiter("\\A");
    final String output = scanner.hasNext() ? scanner.next() : "";
    final int exitCode = process.waitFor();
    return new CliResult(exitCode, output);
  }

  private static boolean isWindows() {
    return System.getProperty(PROP_OS_NAME).toLowerCase().contains("win");
  }

  public static final class CliResult {

    private final int exitCode;
    private final String output;

    private CliResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    public int getExitCode() {
      return exitCode;
    }

    public String getOutput() {
      return output;
    }
  }
}

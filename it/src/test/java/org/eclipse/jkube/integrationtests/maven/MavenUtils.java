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

import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.cli.CliUtils.CliResult;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenUtils {

  private static String mavenLocation;

  private MavenUtils() {
  }

  public static InvocationResult execute(InvocationRequestCustomizer irc)
      throws IOException, InterruptedException, MavenInvocationException {

    final InvocationRequest invocationRequest = new DefaultInvocationRequest();
    irc.customize(invocationRequest);
    return execute(invocationRequest);
  }

  public static InvocationResult execute(InvocationRequest invocationRequest)
      throws IOException, InterruptedException, MavenInvocationException {

    invocationRequest.setBatchMode(true);
    final Invoker invoker = new DefaultInvoker();
    invoker.setMavenHome(new File(getMavenLocation()));
    return invoker.execute(invocationRequest);
  }

  private static String getMavenLocation() throws IOException, InterruptedException {
    if (mavenLocation == null) {
      final CliResult mavenVersion = CliUtils.runCommand(".." + File.separatorChar + "mvnw -v");
      if (mavenVersion.getExitCode() != 0){
        throw new IOException(String.format("Maven: [%s]", mavenVersion.getOutput()));
      }
      final String mavenVersionResult = mavenVersion.getOutput();
      final Pattern mavenHomePattern = Pattern.compile("Maven home:([^\\n\\r]+)", Pattern.MULTILINE);
      final Matcher mavenHomeMatcher =
          mavenHomePattern.matcher(mavenVersionResult);
      if (!mavenHomeMatcher.find()) {
        throw new IOException(String.format("Maven: Incompatible version [%s]", mavenVersionResult));
      }
      mavenLocation = mavenHomeMatcher.group(1).trim();
    }
    return mavenLocation;
  }

  @FunctionalInterface
  public interface InvocationRequestCustomizer {

    void customize(InvocationRequest invocationRequest);
  }

}

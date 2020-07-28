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

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.utils.cli.CommandLineException;

public class MavenInvocationResult implements InvocationResult {
  private final InvocationResult invocationResult;
  private final String stdOut;

  public MavenInvocationResult(InvocationResult invocationResult, String stdOut) {
    this.invocationResult = invocationResult;
    this.stdOut = stdOut;
  }

  public String getStdOut() {
    return stdOut;
  }

  @Override
  public int getExitCode() {
    return invocationResult.getExitCode();
  }

  @Override
  public CommandLineException getExecutionException() {
    return invocationResult.getExecutionException();
  }
}

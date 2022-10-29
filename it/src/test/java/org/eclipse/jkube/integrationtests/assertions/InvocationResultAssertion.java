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
package org.eclipse.jkube.integrationtests.assertions;

import org.apache.maven.shared.invoker.InvocationResult;

public class InvocationResultAssertion {

  private InvocationResultAssertion() {}

  public static void assertInvocation(InvocationResult invocationResult) {
    if (invocationResult.getExitCode() != 0) {
      final StringBuilder message = new StringBuilder("Error in maven invocation, expected <0> exit code\n")
        .append("but was <").append(invocationResult.getExitCode()).append(">");
      if (invocationResult.getExecutionException() != null) {
        message.append("\n").append(invocationResult.getExecutionException().getMessage());
      }
      throw new AssertionError(message.toString());
    }
  }
}

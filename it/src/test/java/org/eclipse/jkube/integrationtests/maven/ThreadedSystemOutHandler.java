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

import org.apache.maven.shared.invoker.SystemOutHandler;

public class ThreadedSystemOutHandler extends SystemOutHandler {
  @Override
  public void consumeLine(String line) {
    super.consumeLine(String.format("[%s] %s", Thread.currentThread().getName(), line));
  }
}

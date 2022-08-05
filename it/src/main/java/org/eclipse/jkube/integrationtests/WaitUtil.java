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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class WaitUtil {
  private WaitUtil() { }

  public static <T> void waitUntilCondition(T t, Predicate<T> condition, TimeUnit timeUnit, int nUnits) throws InterruptedException, TimeoutException {
    for (int i = 0; i < nUnits && !Thread.currentThread().isInterrupted(); i++) {
      if (condition.test(t)) {
        return;
      }
      timeUnit.sleep(1);
    }
    throw new TimeoutException("timed out waiting for condition to satisfy on " + t.toString());
  }
}

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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class WaitUtil {
  private WaitUtil() { }

  // Initialization on demand
  private static class ExecutorServiceHolder {
    public static final ExecutorService INSTANCE = Executors.newCachedThreadPool();
  }

  public static <T> Function<Predicate<T>, CompletableFuture<T>> await(Supplier<T> supplier) {
    return condition -> CompletableFuture.supplyAsync(() -> {
      T result;
      while (!condition.test(result = supplier.get())) {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return result;
    }, ExecutorServiceHolder.INSTANCE);
  }
}

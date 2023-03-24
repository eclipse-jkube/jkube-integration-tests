/*
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
package org.eclipse.jkube.integrationtests.dsl;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {
  public static void main(String... args) throws Exception {
    final var greeting = System.getenv().getOrDefault("GREETING", "Hi Alex!");
    System.out.println(greeting);
    final var server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(200, greeting.length());
      try (var os = exchange.getResponseBody()) {
        os.write(greeting.getBytes(StandardCharsets.UTF_8));
      }
    });
    server.start();
  }
}

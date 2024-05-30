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
package org.eclipse.jkube.integrationtests.buildpacks.simple;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RootHandler implements HttpHandler {
  @Override
  public void handle(HttpExchange exchange) {
    try (OutputStream outputStream = exchange.getResponseBody()) {
      String response = "Simple Application containerized using buildpacks with JKube!";
      exchange.sendResponseHeaders(200, response.length());
      exchange.getResponseHeaders().set("Content-Type", "text/plain");
      outputStream.write(response.getBytes());
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }
}

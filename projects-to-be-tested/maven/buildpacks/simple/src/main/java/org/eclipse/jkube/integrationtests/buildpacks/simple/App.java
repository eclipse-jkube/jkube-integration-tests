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
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.net.httpserver.HttpServer;

public class App {
  private static final int PORT = 8080;

  public static void main(String[] args) {

    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
      server.createContext("/", new RootHandler());
      server.setExecutor(null);
      server.start();
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }
}

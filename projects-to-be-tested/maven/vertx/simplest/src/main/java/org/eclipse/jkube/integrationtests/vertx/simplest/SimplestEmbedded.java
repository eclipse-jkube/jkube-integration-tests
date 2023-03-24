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
package org.eclipse.jkube.integrationtests.vertx.simplest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

import java.util.logging.Logger;

public class SimplestEmbedded extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(SimplestEmbedded.class.getName());
  @Override
  public void start(Promise<Void> startFuture) {
    Vertx.vertx().createHttpServer()
      .requestHandler(SimplestEmbedded::requestHandler)
      .listen(8080, listenerHandler(startFuture));
  }

  private static Handler<AsyncResult<HttpServer>> listenerHandler(Promise<Void> verticleStart) {
    return http -> {
      if (http.succeeded()) {
        verticleStart.complete();
        LOG.info("Vert.x test application is ready");
      } else {
        verticleStart.fail(http.cause());
      }
    };
  }

  private static void requestHandler(HttpServerRequest request) {
    request.response().setStatusCode(200).end("Hello from JKube!");
  }
}

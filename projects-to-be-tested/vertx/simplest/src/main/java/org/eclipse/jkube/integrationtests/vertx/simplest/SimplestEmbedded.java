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
package org.eclipse.jkube.integrationtests.vertx.simplest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

public class SimplestEmbedded extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) {
    Vertx.vertx().createHttpServer()
      .requestHandler(SimplestEmbedded::requestHandler)
      .listen(8080, listenerHandler(startFuture));
  }

  private static Handler<AsyncResult<HttpServer>> listenerHandler(Future<Void> verticleStart) {
    return asyncResult -> {
      if (asyncResult.succeeded()) {
        verticleStart.complete();
      } else {
        verticleStart.fail(asyncResult.cause());
      }
    };
  }

  private static void requestHandler(HttpServerRequest request) {
    request.response().setStatusCode(200).end("Hello from JKube!");
  }
}

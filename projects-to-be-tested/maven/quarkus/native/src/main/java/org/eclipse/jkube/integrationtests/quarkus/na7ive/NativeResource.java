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
package org.eclipse.jkube.integrationtests.quarkus.na7ive;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class NativeResource {

  private NativeService nativeService;

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response get() {
      return Response.ok(nativeService.getNativeResource()).build();
    }

  @Inject
  public void setNativeService(NativeService nativeService) {
    this.nativeService = nativeService;
  }
}

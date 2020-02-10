package org.eclipse.jkube.integrationtests.thorntail.microprofile;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("")
public class MicroProfileResource {

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response get() {
    return Response.ok("JKube from Thorntail rocks!").build();
  }
}

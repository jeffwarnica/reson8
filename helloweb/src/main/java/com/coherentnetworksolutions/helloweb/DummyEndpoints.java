package com.coherentnetworksolutions.helloweb;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.micrometer.core.instrument.MeterRegistry;


@Path("/api/test")
public class DummyEndpoints {

    @Inject
    MeterRegistry meterRegistry;

    @GET
    @Path("/success")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello";
    }

    @GET
    @Path("/notfound")
    @Produces(MediaType.TEXT_PLAIN)
    public String notFound() {
        throw new NotFoundException();
    }

    @GET
    @Path("/error")
    @Produces(MediaType.TEXT_PLAIN)
    public String error() {
        throw new RuntimeException("Simulated error");
    }
}

package com.coherentnetworksolutions.reson8.k8s.client;

import com.coherentnetworksolutions.reson8.k8s.client.ThanosMetricPoller.ThanosLabelResponse;
import com.coherentnetworksolutions.reson8.k8s.client.ThanosMetricPoller.ThanosResponse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;


public interface ThanosRestClient {
    @GET
    @Path("/query")
    ThanosResponse query(@QueryParam("query") String promql,
            @HeaderParam("Authorization") String token);

    @GET
    @Path("/labels") // Used for the health check
    ThanosLabelResponse checkHealth(@HeaderParam("Authorization") String token);            
}
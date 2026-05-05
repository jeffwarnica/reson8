package com.coherentnetworksolutions.reson8.k8s.client;

import java.io.IOException;

/**
 * Prometheus HTTP API v1 subset against Thanos querier ({@code /api/v1/query}, {@code /api/v1/labels}).
 * Implemented with {@link java.net.http.HttpClient} so a custom TLS trust stack applies; Quarkus RESTEasy Reactive
 * programmatic clients do not support supplying an {@code SSLContext}.
 */
public interface ThanosPrometheusApi {

    ThanosMetricPoller.ThanosLabelResponse fetchLabels(String authorizationHeader) throws IOException;

    ThanosMetricPoller.ThanosResponse fetchQuery(String promql, String authorizationHeader) throws IOException;
}

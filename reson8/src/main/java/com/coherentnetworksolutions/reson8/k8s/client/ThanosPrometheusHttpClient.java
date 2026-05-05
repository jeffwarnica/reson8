package com.coherentnetworksolutions.reson8.k8s.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thanos/Prometheus HTTP client. TLS trust uses the JVM default trust store, which is populated by
 * {@code update-ca-trust} in the pod entrypoint (run-with-ca-update.sh) before the JVM starts.
 * No application-level TLS configuration is needed or allowed.
 */
public final class ThanosPrometheusHttpClient implements ThanosPrometheusApi {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient httpClient;
    private final URI apiV1Root;

    private ThanosPrometheusHttpClient(HttpClient httpClient, URI apiV1Root) {
        this.httpClient = httpClient;
        this.apiV1Root = apiV1Root;
    }

    public static ThanosPrometheusApi create(Reson8Config config) {
        String baseUrl = ThanosApiBaseUri.resolve(config);
        URI apiRoot = URI.create(baseUrl);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new ThanosPrometheusHttpClient(http, apiRoot);
    }

    @Override
    public ThanosMetricPoller.ThanosLabelResponse fetchLabels(String authorizationHeader) throws IOException {
        URI uri = appendPath(apiV1Root, "labels");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorizationHeader)
                .GET()
                .build();
        return send(req, ThanosMetricPoller.ThanosLabelResponse.class);
    }

    @Override
    public ThanosMetricPoller.ThanosResponse fetchQuery(String promql, String authorizationHeader)
            throws IOException {
        String encoded =
                "query=" + URLEncoder.encode(promql, StandardCharsets.UTF_8);
        URI uri = appendPath(apiV1Root, "query?" + encoded);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorizationHeader)
                .GET()
                .build();
        return send(req, ThanosMetricPoller.ThanosResponse.class);
    }

    private static URI appendPath(URI apiRoot, String pathAndOptionalQuery) {
        String base = apiRoot.toString();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return URI.create(base + pathAndOptionalQuery);
    }

    private <T> T send(HttpRequest req, Class<T> type) throws IOException {
        try {
            HttpResponse<String> res =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IOException("Thanos HTTP " + res.statusCode() + ": " + res.body());
            }
            return MAPPER.readValue(res.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thanos request interrupted", e);
        }
    }
}

package com.coherentnetworksolutions.reson8.k8s.client;

import java.io.File;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

/**
 * Resolves the Prometheus/Thanos HTTP API base ({@code .../api/v1}) consistently for
 * {@link ThanosMetricPoller} and {@link ThanosConnectivityCheck}.
 */
public final class ThanosApiBaseUri {

    private ThanosApiBaseUri() {}

    public static String resolve(Reson8Config config) {
        if (new File("/var/run/secrets/kubernetes.io").exists()) {
            return config.k8s().thanos().inClusterUrl();
        }
        String url = config.k8s().thanos().baseUrl();
        if (!url.endsWith("/api/v1")) {
            return url.endsWith("/") ? url + "api/v1" : url + "/api/v1";
        }
        return url;
    }
}

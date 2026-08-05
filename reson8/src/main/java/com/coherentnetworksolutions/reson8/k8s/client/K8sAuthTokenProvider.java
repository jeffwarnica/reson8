package com.coherentnetworksolutions.reson8.k8s.client;

import io.fabric8.kubernetes.api.model.authentication.TokenRequest;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Provides a Kubernetes/OpenShift bearer token for outbound HTTP calls (e.g. Thanos).
 * <p>
 * Extracted from {@link K8sClient} so that {@link ThanosMetricPoller} can obtain the
 * token without depending on the full {@code K8sClient} bean.
 */
@ApplicationScoped
public class K8sAuthTokenProvider {

    @Inject
    KubernetesClient client;

    /**
     * Returns the cluster bearer token. First tries the client's in-memory OAuth token
     * (set from env / kubeconfig). If absent, requests a short-lived token for the
     * {@code reson8} service account.
     *
     * @return bearer token string, or {@code null} if unavailable
     */
    public String getToken() {
        String token = client.getConfiguration().getOauthToken();

        if (token == null || token.isEmpty()) {
            try {
                var tr = client.serviceAccounts()
                        .inNamespace(client.getNamespace())
                        .withName("reson8")
                        .tokenRequest(new TokenRequest());
                token = tr.getStatus().getToken();
                Log.debug("K8sAuthTokenProvider: exchanged certs for service account token.");
            } catch (Exception e) {
                Log.error("K8sAuthTokenProvider: failed to request token: " + e.getMessage());
            }
        }
        return token;
    }
}

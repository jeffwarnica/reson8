package com.coherentnetworksolutions.reson8.manager.config;

import java.util.List;
import java.util.Optional;

import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap.Point;
import com.coherentnetworksolutions.reson8.k8s.client.ThanosApiBaseUri;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "reson8")
public interface Reson8Config {
    @WithName("audio-path")
    @WithDefault("${RESON8_AUDIO_PATH:${user.dir}/src/main/resources}")
    String audioPath();

    /**
     * SPA access tiers: JWT {@code groups} (and optional anonymous sentinel) mapped to admin / viewer / stream-only.
     * Tier resolution precedence when evaluating a subject is {@code admin > viewer > stream}.
     */
    SecurityConfig security();

    /**
     * When {@code quarkus.oidc.enabled=true}, drives how {@code quarkus.oidc.auth-server-url} is set: query the cluster
     * OAuth authorization server metadata for a public {@code issuer}, or use an explicit URL.
     */
    @WithName("openshift-oauth")
    OpenshiftOauthConfig openshiftOauth();

    K8sConfig k8s();

    SignalMap signalMap();

    List<Soundscape> soundscapes();

    /**
     * Group lists for SPA security tiers. Empty lists mean no subject qualifies for that tier via group membership.
     * The same group must not appear in more than one list — startup validation fails if any overlap exists.
     */
    interface SecurityConfig {
        /** IdP groups granting admin tier (full control). */
        @WithName("admin-groups")
        Optional<List<String>> adminGroups();

        /** IdP groups granting viewer tier (read + stream; mutations disabled in UI). */
        @WithName("viewer-groups")
        Optional<List<String>> viewerGroups();

        /**
         * Stream-only tier groups plus optional {@link #anonymousStreamSentinel()} token for unauthenticated stream access.
         */
        @WithName("stream-groups")
        Optional<List<String>> streamGroups();

        /**
         * When this exact string appears in {@link #streamGroups()}, unauthenticated callers may resolve to stream-only
         * tier (see deployment docs). Operators may change the token; keep it out of real IdP group namespaces.
         */
        @WithDefault("__anonymous__")
        @WithName("anonymous-stream-sentinel")
        String anonymousStreamSentinel();

        /**
         * When {@code true}, SPA may show an OIDC login entry point when OIDC is enabled for the deployment.
         */
        @WithDefault("true")
        @WithName("login-available")
        boolean loginAvailable();

        /**
         * Enables dev-tier cookie simulation for local dev and tests; must stay {@code false} in prod.
         */
        @WithDefault("false")
        @WithName("dev-tier-cookie-enabled")
        boolean devTierCookieEnabled();

        /**
         * When {@code true}, tier checks reject callers without capability on audio control/drop/debug/stream routes.
         */
        @WithDefault("true")
        @WithName("endpoint-authorization-enabled")
        boolean endpointAuthorizationEnabled();
    }

    /**
     * OpenShift / Kubernetes OAuth discovery for the default Quarkus OIDC tenant.
     */
    interface OpenshiftOauthConfig {

        /**
         * When {@code true} (default), GET {@link #metadataUrl()} or the in-cluster API server metadata document and use
         * its {@code issuer} as {@code quarkus.oidc.auth-server-url}. When {@code false}, set {@link #authServerUrl()} or
         * issuer env vars ({@code OPENSHIFT_AUTH_ISSUER_URL}, {@code OIDC_AUTH_SERVER_URL}) — see bootstrap factory.
         */
        @WithDefault("true")
        @WithName("discovery-enabled")
        boolean discoveryEnabled();

        /**
         * Full URL for {@code /.well-known/oauth-authorization-server} on the API server; when absent, built from
         * {@code KUBERNETES_SERVICE_HOST} / {@code KUBERNETES_SERVICE_PORT} when running in a pod.
         */
        @WithName("metadata-url")
        Optional<String> metadataUrl();

        /**
         * Explicit issuer / auth-server URL; when present and non-blank, discovery is skipped. Operators typically set this in
         * mounted YAML using env expansion (e.g. {@code "${OPENSHIFT_AUTH_ISSUER_URL}"}); {@link com.coherentnetworksolutions.reson8.manager.config.oidc.Reson8OidcBootstrapConfigSourceFactory}
         * also falls back to {@code OPENSHIFT_AUTH_ISSUER_URL} / {@code OIDC_AUTH_SERVER_URL} when this property is unset.
         */
        @WithName("auth-server-url")
        Optional<String> authServerUrl();

    }

    interface K8sConfig {
        String cluster();

        // NamespaceConfig namespaces();

        String map();

        ThanosConfig thanos();
    }

    interface ThanosConfig {
        /**
         * Prometheus/Thanos HTTP API host prefix used when not running inside a pod (no service-account secrets dir).
         * {@link ThanosApiBaseUri} appends {@code /api/v1} when needed.
         * <p>
         * Outside-cluster {@code quarkus:dev}: set env {@code RESON8_K8S_THANOS_BASE_URL} to your cluster Route (see
         * {@code application-local-DIST.properties} / {@code DEPLOY.md}); do not commit that URL.
         */
        @WithDefault("https://thanos-querier.openshift-monitoring.svc.cluster.local:9091/")
        @WithName("base-url")
        String baseUrl();

        /**
         * In-cluster Thanos Querier URL used when the application is running inside
         * Kubernetes and the in-cluster secrets directory exists.
         * <p>
         * Defaults to the standard OpenShift monitoring endpoint. Override via
         * {@code reson8.k8s.thanos.in-cluster-url} when running in a non-standard
         * cluster setup.
         */
        @WithDefault("https://thanos-querier.openshift-monitoring.svc.cluster.local:9091/api/v1")
        @WithName("in-cluster-url")
        String inClusterUrl();

        /**
         * When {@code true}, {@link com.coherentnetworksolutions.reson8.k8s.client.ThanosConnectivityCheck}
         * enforces that the Thanos querier {@code /api/v1/labels} endpoint returns success. Disable in
         * tests ({@code %test}) that have no querier.
         */
        @WithDefault("true")
        @WithName("readiness-check")
        boolean readinessCheck();
    }

    /**
     * Legacy namespace scope placeholder.
     * <p>
     * Runtime collection is currently cluster-wide in {@code K8sClient} for events, pending pods,
     * and deployment health. This mapping remains only for backwards compatibility with older
     * overlays and should not be interpreted as an active namespace filter.
     */
    // interface NamespaceConfig {
    //     boolean all();
    // }

    interface SignalMap {
        String name();

        @WithName("default-soundscape")
        String defaultSoundscape();

        List<InputMapping> inputs();
    }

    interface InputMapping {
        String name();

        String sound();

        @WithName("source-type")
        Optional<SourceType> sourceType();

        Optional<String> query();

        @WithName("metric")
        Optional<ClusterMetric> metric();

        Optional<CurveConfig> curve();
    }

    public enum SourceType {
        @WithName("prometheus") PROMETHEUS,
        @WithName("kubernetes_event") KUBERNETES_EVENT,
        @WithName("kubernetes_stats") KUBERNETES_STATS,
    }

    public enum ClusterMetric {
        @WithName("cpu") CPU,
        @WithName("memory") MEMORY,
        @WithName("node_readiness") NODE_READINESS,
        @WithName("pending_pods") PENDING_PODS,
        @WithName("deployment_health") DEPLOYMENT_HEALTH,
    }

    interface Soundscape {
        String name();

        List<SoundDefinition> sounds();
    }

    interface SoundDefinition {
        String name();

        @WithName("sound-type")
        SoundType soundType();

        Optional<LoopConfig> loop();
        Optional<DropConfig> drop();
        Optional<ProceduralConfig> procedural();
        Optional<StochasticConfig> stochastic();
    }

    public enum SoundType {
        @WithName("drop") DROP,
        @WithName("loop") LOOP,
        @WithName("procedural") PROCEDURAL,
        @WithName("stochastic") STOCHASTIC
    }

    interface StochasticConfig {
        String directory();
        @WithDefault("32")
        int maxSimultaneous();
        @WithDefault("100.0")
        @WithName("ceiling")
        Double ceiling();
        @WithDefault("0.1")
        double pitchRandomization();
        @WithDefault("0.05")
        @WithName("smoothingrate")
        double smoothingrate();
    }

    interface LoopConfig {
        String filename();

        @WithDefault("1.0")
        @WithName("output-scale")
        Double outputScale();
        @WithDefault("0.02")
        @WithName("smoothingrate")
        Double smoothingrate();
    }

    interface DropConfig {
        String filename();
        @WithDefault("100.0")
        @WithName("ceiling")
        Double ceiling();
    }

    interface ProceduralConfig {
        @WithName("generator")
        GeneratorType generatorType();

        @WithDefault("1.0")
        @WithName("output-scale")
        Double outputScale();

        @WithDefault("0.02")
        @WithName("smoothingrate")
        Double smoothingrate();

        @WithName("intensity")
        @WithDefault("50.0")
        Double intensity();
    }

    public enum GeneratorType {
        @WithName("wind") WIND
    }

    interface CurveConfig {
        @WithDefault("linear")
        Interpolation interpolation();

        List<CurvePoint> points();
    }

    public enum Interpolation {
        @WithName("linear")   LINEAR,
        @WithName("smooth")   SMOOTH,
        @WithName("monotone") MONOTONE
    }

    interface CurvePoint {
        @WithName("in")
        double input();

        @WithName("out")
        double output();

        default Point toRecord() {
            return new Point(input(), output());
        }
    }
}

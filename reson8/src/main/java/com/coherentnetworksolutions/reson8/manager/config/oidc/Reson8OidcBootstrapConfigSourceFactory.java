package com.coherentnetworksolutions.reson8.manager.config.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;

/**
 * Supplies {@code quarkus.oidc.*} defaults from {@code reson8.oidc.*} and resolves
 * {@code quarkus.oidc.auth-server-url} via {@code reson8.openshift-oauth.*} (explicit issuer or
 * metadata discovery).
 *
 * <p>TLS trust for OIDC connections uses the JVM default trust store. The pod entrypoint
 * (run-with-ca-update.sh) calls {@code update-ca-trust} before the JVM starts, so all cluster CAs
 * (service CA, ingress CA, kube CA) are present in the OS trust store — no per-client TLS
 * configuration is needed or allowed.
 */
public final class Reson8OidcBootstrapConfigSourceFactory implements ConfigSourceFactory {

    private static final Logger LOGGER =
            Logger.getLogger(Reson8OidcBootstrapConfigSourceFactory.class.getName());

    static final String QUARKUS_OIDC_ENABLED = "quarkus.oidc.enabled";
    static final String QUARKUS_OIDC_AUTH_SERVER_URL = "quarkus.oidc.auth-server-url";

    static final String RESON8_OIDC_ENABLED = "reson8.oidc.enabled";
    static final String RESON8_OIDC_APPLICATION_TYPE = "reson8.oidc.application-type";
    static final String RESON8_OIDC_FORCE_REDIRECT_HTTPS = "reson8.oidc.force-redirect-https-scheme";
    static final String RESON8_OIDC_CLIENT_ID = "reson8.oidc.client-id";
    static final String RESON8_OIDC_CLIENT_SECRET = "reson8.oidc.client-secret";

    static final String KEY_DISCOVERY = "reson8.openshift-oauth.discovery-enabled";
    static final String KEY_METADATA = "reson8.openshift-oauth.metadata-url";
    static final String KEY_AUTH_SERVER = "reson8.openshift-oauth.auth-server-url";

    private static final Path SA_TOKEN_PATH =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
    private static final Path SA_NAMESPACE_PATH =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private enum Effective {
        QUARKUS_FALSE,
        RESON8_FALSE,
        ON
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        LOGGER.info("Reson8OidcBootstrapConfigSourceFactory: resolving OIDC configuration.");
        Effective effective = resolveEffective(context);
        if (effective == Effective.QUARKUS_FALSE) {
            LOGGER.info("OIDC bootstrap: quarkus.oidc.enabled=false found in config — skipping OIDC setup. "
                    + "Suppressing login-available so the SPA does not expose a broken /login endpoint.");
            return java.util.Collections.singletonList(
                    new StaticBootstrapSource(Map.of("reson8.security.login-available", "false")));
        }
        if (effective == Effective.RESON8_FALSE) {
            LOGGER.info("OIDC bootstrap: reson8.oidc.enabled=false — emitting quarkus.oidc.enabled=false. "
                    + "Suppressing login-available.");
            return java.util.Collections.singletonList(
                    new StaticBootstrapSource(Map.of(
                            QUARKUS_OIDC_ENABLED, "false",
                            "reson8.security.login-available", "false")));
        }

        LOGGER.info("OIDC bootstrap: building quarkus.oidc.* configuration.");
        Map<String, String> props = new HashMap<>();
        props.put(QUARKUS_OIDC_ENABLED, "true");

        String applicationType = stringProperty(context, RESON8_OIDC_APPLICATION_TYPE, "hybrid");
        props.put("quarkus.oidc.application-type", applicationType);
        LOGGER.info("OIDC bootstrap: application-type=" + applicationType);

        props.put(
                "quarkus.oidc.authentication.force-redirect-https-scheme",
                Boolean.toString(booleanProperty(context, RESON8_OIDC_FORCE_REDIRECT_HTTPS, true)));
        // quarkus.oidc.authentication.optional is not a valid Quarkus 3.x config key; hybrid mode
        // already handles the optional-auth semantics for Bearer vs browser flows.
        //
        // Group extraction from UserInfo is performed by OpenShiftGroupsAugmentor, which reads the
        // 'groups' array from the already-fetched UserInfo object and adds them as roles directly.
        // roles.source=userinfo is not used because OpenShift's opaque access_token produces an
        // internal id_token with no claims, so Quarkus cannot reliably propagate groups that way.

        // Client ID for the ServiceAccount-as-OAuthClient model in OpenShift.
        // OpenShift requires client_id = system:serviceaccount:<namespace>:<sa-name>.
        // Derive from the SA namespace mount unless explicitly overridden.
        String clientId = resolveClientId(context);
        props.put("quarkus.oidc.client-id", clientId);
        LOGGER.info("OIDC bootstrap: client-id=" + clientId);

        // Client secret = the ServiceAccount token, used as the OAuth client credential.
        // OpenShift accepts the SA token as the secret for SA-based OAuthClients.
        // Explicit RESON8_OIDC_CLIENT_SECRET / OIDC_CLIENT_SECRET env var overrides this.
        //
        // MUST use client_secret_post (POST body), NOT client_secret_basic (HTTP Basic auth).
        // HTTP Basic encodes credentials as base64(client_id:client_secret) and splits on the
        // first colon. SA client IDs contain colons (system:serviceaccount:reson8:reson8), so
        // the OAuth server only sees "system" as the client_id and returns unauthorized_client.
        resolvedClientSecret(context).ifPresent(secret -> {
            props.put("quarkus.oidc.credentials.client-secret.value", secret);
            props.put("quarkus.oidc.credentials.client-secret.method", "post");
        });

        // TLS uses the JVM default trust store — populated by update-ca-trust in run-with-ca-update.sh
        // before the JVM starts. No quarkus.tls.* or quarkus.oidc.tls.* configuration is emitted.

        // OpenShift's built-in OAuth server is OAuth 2.0 only (not OIDC):
        // /.well-known/openid-configuration returns a paths listing, not a discovery document.
        // Disable Quarkus OIDC discovery and configure the OAuth 2.0 endpoints explicitly.
        // Documented OpenShift OAuth endpoints: https://docs.openshift.com/container-platform/latest/authentication/configuring-internal-oauth.html
        props.put("quarkus.oidc.discovery-enabled", "false");
        props.put("quarkus.oidc.authorization-path", "/oauth/authorize");
        props.put("quarkus.oidc.token-path", "/oauth/token");

        // OpenShift SA OAuth clients are restricted to: user:info, user:check-access,
        // and role:<name>:<namespace>. The scope user:full is explicitly denied for SA clients
        // (OpenShift returns error=access_denied&error_description=scope+denied%3A+user%3Afull).
        // user:info provides the user's identity (username, UID, groups) — sufficient for authn.
        // Reference: https://docs.openshift.com/container-platform/latest/authentication/using-service-accounts-as-oauth-client.html
        props.put("quarkus.oidc.authentication.scopes", "user:info");
        // The 'openid' scope (added automatically by Quarkus OIDC for OIDC providers) is NOT
        // accepted by OpenShift SA OAuth clients — it causes a login loop (silent rejection,
        // form cleared, shown again). Disable automatic 'openid' scope addition explicitly.
        // Reference: quarkus.io/guides/security-oidc-configuration-properties-reference
        props.put("quarkus.oidc.authentication.add-openid-scope", "false");

        // OpenShift OAuth 2.0 does NOT return an OIDC id_token — only an access_token.
        // Quarkus OIDC requires id_token by default; disable that requirement so Quarkus
        // generates an internal id_token from the access_token instead.
        // OidcTenantConfig.Authentication.idTokenRequired() javadoc:
        //   "Disable this property only when you need to use the authorization code flow
        //    with OAuth2 providers which do not return ID token — an internal IdToken is
        //    generated in such cases."
        props.put("quarkus.oidc.authentication.id-token-required", "false");

        // Token validation via OpenShift's User API — no introspection endpoint needed.
        // After the authorization code exchange, Quarkus sends the access_token as Bearer to this
        // endpoint. OpenShift validates the token and returns the User resource (200) if valid, or
        // 401 if the token is expired or invalid.
        //
        // user-info-path accepts an absolute URL (Quarkus OIDC docs: "relative path or absolute
        // URL"). Using the in-cluster Kubernetes API server hostname (always reachable from the pod,
        // trusted by the OS store populated by update-ca-trust).
        //
        // The external OAuth route (oauth-openshift.apps.*) does NOT expose /oauth/token/introspect
        // (returns 404). The internal API server requires RBAC + Bearer auth (not RFC 7662 Basic),
        // so Quarkus built-in introspection cannot reach it without a custom bridge.
        //
        // Note: user-info-required is automatically enabled by Quarkus when id-token-required=false.
        props.put("quarkus.oidc.user-info-path",
                "https://kubernetes.default.svc:443/apis/user.openshift.io/v1/users/~");
        props.put("quarkus.oidc.token.verify-access-token-with-user-info", "true");
        // OpenShift User resource identifies the user via metadata.name, not the OIDC 'sub' claim.
        // Quarkus OIDC supports nested claim paths via '.' notation.
        props.put("quarkus.oidc.token.principal-claim", "metadata.name");

        // Do not force RP-initiated logout properties here.
        // Some OAuth providers (including OpenShift OAuth metadata flow) do not advertise
        // end_session_endpoint, and forcing logout config can abort startup.

        Optional<String> explicitIssuer = resolvedAuthServerUrl(context);
        if (explicitIssuer.isPresent()) {
            String url = trimTrailingSlash(explicitIssuer.get());
            props.put(QUARKUS_OIDC_AUTH_SERVER_URL, url);
            LOGGER.info("OIDC bootstrap: auth-server-url set from explicit config/env: " + url);
            logEffectiveOidcEndpoints(url);
            return java.util.Collections.singletonList(new StaticBootstrapSource(Map.copyOf(props)));
        }

        boolean discovery = booleanProperty(context, KEY_DISCOVERY, true);
        if (!discovery) {
            throw new IllegalStateException(
                    "quarkus.oidc is enabled and reson8.openshift-oauth.discovery-enabled=false, but "
                            + "no issuer URL is set (reson8.openshift-oauth.auth-server-url, OPENSHIFT_AUTH_ISSUER_URL, or "
                            + "OIDC_AUTH_SERVER_URL).");
        }

        String metadataUrl = stringPropertyOptional(context, KEY_METADATA).orElse(null);
        if (metadataUrl == null || metadataUrl.isBlank()) {
            metadataUrl = defaultInClusterOAuthMetadataUrl();
        }
        if (metadataUrl == null || metadataUrl.isBlank()) {
            // Not in a pod and no explicit issuer configured — this is a local/CI build.
            // The ConfigSourceFactory runs at both augmentation (build) and runtime.
            // At build time there is no Kubernetes API server to discover the issuer from.
            // Return a placeholder so the build succeeds; the real URL is resolved at runtime.
            LOGGER.warning("OIDC bootstrap: not in a Kubernetes pod and no auth-server-url configured. "
                    + "Using placeholder for build-time augmentation; runtime will resolve the real URL.");
            props.put(QUARKUS_OIDC_AUTH_SERVER_URL, "https://placeholder.build.invalid");
            return java.util.Collections.singletonList(new StaticBootstrapSource(Map.copyOf(props)));
        }

        LOGGER.info("OIDC bootstrap: discovering issuer from metadata URL: " + metadataUrl);

        try {
            String issuer = fetchIssuerFromMetadata(metadataUrl.trim());
            props.put(QUARKUS_OIDC_AUTH_SERVER_URL, issuer);
            LOGGER.info("OIDC bootstrap: auth-server-url resolved via discovery: " + issuer);
            logEffectiveOidcEndpoints(issuer);
            return java.util.Collections.singletonList(new StaticBootstrapSource(Map.copyOf(props)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while resolving OAuth issuer from metadata at " + metadataUrl + ".",
                    e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to resolve OAuth issuer from metadata at "
                            + metadataUrl
                            + ". Set reson8.openshift-oauth.auth-server-url explicitly, or fix connectivity / RBAC to "
                            + "the API server.",
                    e);
        }
    }

    private static Effective resolveEffective(ConfigSourceContext context) {
        ConfigValue q = context.getValue(QUARKUS_OIDC_ENABLED);
        if (q != null && q.getRawValue() != null && !q.getRawValue().isBlank()) {
            boolean on = Boolean.parseBoolean(q.getValue().trim());
            return on ? Effective.ON : Effective.QUARKUS_FALSE;
        }
        boolean reson8On = booleanProperty(context, RESON8_OIDC_ENABLED, true);
        return reson8On ? Effective.ON : Effective.RESON8_FALSE;
    }

    private static boolean booleanProperty(ConfigSourceContext context, String key, boolean defaultValue) {
        ConfigValue cv = context.getValue(key);
        if (cv == null) {
            return defaultValue;
        }
        String v = cv.getValue();
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v.trim());
    }

    private static String stringProperty(ConfigSourceContext context, String key, String defaultValue) {
        ConfigValue cv = context.getValue(key);
        if (cv == null) {
            return defaultValue;
        }
        String v = cv.getValue();
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v.trim();
    }

    private static Optional<String> stringPropertyOptional(ConfigSourceContext context, String key) {
        ConfigValue cv = context.getValue(key);
        if (cv == null) {
            return Optional.empty();
        }
        String v = cv.getValue();
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(v.trim());
    }

    static Optional<String> resolvedAuthServerUrl(ConfigSourceContext context) {
        Optional<String> fromConfig = stringPropertyOptional(context, KEY_AUTH_SERVER).filter(s -> !s.isBlank());
        if (fromConfig.isPresent()) {
            return fromConfig;
        }
        Optional<String> fromOpenShift = Optional.ofNullable(System.getenv("OPENSHIFT_AUTH_ISSUER_URL"))
                .filter(s -> !s.isBlank());
        if (fromOpenShift.isPresent()) {
            return fromOpenShift;
        }
        return Optional.ofNullable(System.getenv("OIDC_AUTH_SERVER_URL")).filter(s -> !s.isBlank());
    }

    static String resolveClientId(ConfigSourceContext context) {
        Optional<String> explicit = stringPropertyOptional(context, RESON8_OIDC_CLIENT_ID)
                .filter(s -> !s.isBlank());
        if (explicit.isPresent()) {
            return explicit.get();
        }
        String fromEnv = System.getenv("OIDC_CLIENT_ID");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        // Derive from SA namespace mount — the standard in-cluster path.
        String namespace = readFileQuietly(SA_NAMESPACE_PATH);
        if (namespace != null && !namespace.isBlank()) {
            return "system:serviceaccount:" + namespace.trim() + ":reson8";
        }
        LOGGER.warning("OIDC bootstrap: SA namespace mount not found; defaulting client-id to 'reson8'. "
                + "Set OIDC_CLIENT_ID or reson8.oidc.client-id to override.");
        return "reson8";
    }

    static Optional<String> resolvedClientSecret(ConfigSourceContext context) {
        Optional<String> fromConfig =
                stringPropertyOptional(context, RESON8_OIDC_CLIENT_SECRET).filter(s -> !s.isBlank());
        if (fromConfig.isPresent()) {
            return fromConfig;
        }
        String fromEnv = System.getenv("OIDC_CLIENT_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv.trim());
        }
        // Fall back to the ServiceAccount token — OpenShift accepts it as the OAuth client secret
        // for SA-based OAuthClients. The token is auto-rotated by Kubernetes; a new pod starts
        // with the current token. For longer-lived scenarios, mount a static SA token Secret.
        String saToken = readFileQuietly(SA_TOKEN_PATH);
        if (saToken != null && !saToken.isBlank()) {
            LOGGER.info("OIDC bootstrap: credentials.secret sourced from SA token mount (no explicit secret configured).");
            return Optional.of(saToken.trim());
        }
        LOGGER.warning("OIDC bootstrap: no client secret found (config, OIDC_CLIENT_SECRET, or SA token mount). "
                + "Token introspection will likely fail without credentials.");
        return Optional.empty();
    }

    private static String readFileQuietly(Path path) {
        try {
            if (Files.isReadable(path)) {
                return Files.readString(path, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            LOGGER.warning("OIDC bootstrap: could not read " + path + ": " + e.getMessage());
        }
        return null;
    }

    static String defaultInClusterOAuthMetadataUrl() {
        String host = System.getenv("KUBERNETES_SERVICE_HOST");
        String port = System.getenv("KUBERNETES_SERVICE_PORT");
        if (host == null || host.isBlank() || port == null || port.isBlank()) {
            return null;
        }
        return "https://" + host + ":" + port + "/.well-known/oauth-authorization-server";
    }

    /**
     * Fetches the OAuth authorization server metadata document and extracts the {@code issuer} field.
     * Uses the JVM default SSL trust (updated by run-with-ca-update.sh before JVM startup).
     */
    static String fetchIssuerFromMetadata(String metadataUrl)
            throws IOException, InterruptedException {
        URI uri = URI.create(metadataUrl);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        HttpRequest.Builder req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
        String tok = readFileQuietly(SA_TOKEN_PATH);
        if (tok != null && !tok.isEmpty()) {
            req.header("Authorization", "Bearer " + tok);
        }

        HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("metadata HTTP " + res.statusCode() + ": " + res.body());
        }
        return OAuthAuthorizationServerMetadata.requireIssuer(res.body());
    }

    private static void logEffectiveOidcEndpoints(String authServerUrl) {
        LOGGER.info("OIDC bootstrap: issuer=" + authServerUrl + " scopes=user:info method=client_secret_post");
        LOGGER.fine("OIDC bootstrap: discovery-enabled=false (OpenShift OAuth is OAuth 2.0, not OIDC)");
        LOGGER.fine("OIDC bootstrap: authorization-endpoint=" + authServerUrl + "/oauth/authorize");
        LOGGER.fine("OIDC bootstrap: token-endpoint=" + authServerUrl + "/oauth/token");
        LOGGER.fine("OIDC bootstrap: token-auth-method=client_secret_post (SA client IDs contain colons; Basic auth would split on first colon)");
        LOGGER.fine("OIDC bootstrap: id-token-required=false (OpenShift OAuth 2.0 returns no id_token; Quarkus generates internal token)");
        LOGGER.fine("OIDC bootstrap: groups extracted by OpenShiftGroupsAugmentor from UserInfo attribute");
        LOGGER.fine("OIDC bootstrap: user-info-endpoint=https://kubernetes.default.svc:443/apis/user.openshift.io/v1/users/~ (token validation via OpenShift User API)");
        LOGGER.fine("OIDC bootstrap: scopes=user:info (user:full denied for SA OAuth clients; openid scope suppressed)");
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static final class StaticBootstrapSource implements ConfigSource {

        private final Map<String, String> props;

        private StaticBootstrapSource(Map<String, String> props) {
            this.props = props;
        }

        @Override
        public Map<String, String> getProperties() {
            return props;
        }

        @Override
        public Set<String> getPropertyNames() {
            return props.keySet();
        }

        @Override
        public String getValue(String propertyName) {
            return props.get(propertyName);
        }

        @Override
        public String getName() {
            return "reson8-oidc-bootstrap";
        }

        @Override
        public int getOrdinal() {
            return 450;
        }
    }
}

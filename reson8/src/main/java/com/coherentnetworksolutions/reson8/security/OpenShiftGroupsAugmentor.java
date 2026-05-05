package com.coherentnetworksolutions.reson8.security;

import java.util.logging.Logger;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Promotes OpenShift groups from the already-fetched OIDC UserInfo into {@code identity.getRoles()}.
 *
 * <p>OpenShift's OAuth 2.0 token endpoint returns an opaque access_token (sha256~…), not a JWT.
 * Quarkus OIDC generates an internal id_token with no user claims, so {@code roles.source=userinfo}
 * config does not reliably propagate groups into the identity. This augmentor reads the
 * {@code UserInfo} attribute that Quarkus already fetched from
 * {@code /apis/user.openshift.io/v1/users/~} and adds the {@code groups} array directly
 * as roles, making them available to {@link AccessTierResolver}.
 */
@ApplicationScoped
public class OpenShiftGroupsAugmentor implements SecurityIdentityAugmentor {

    private static final Logger LOG = Logger.getLogger(OpenShiftGroupsAugmentor.class.getName());

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        UserInfo userInfo = identity.getAttribute("userinfo");
        if (userInfo == null) {
            return Uni.createFrom().item(identity);
        }

        JsonObject json = userInfo.getJsonObject();
        if (json == null) {
            return Uni.createFrom().item(identity);
        }

        JsonArray groups = json.getJsonArray("groups");
        if (groups == null || groups.isEmpty()) {
            return Uni.createFrom().item(identity);
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        for (jakarta.json.JsonValue v : groups) {
            if (v instanceof JsonString js && !js.getString().isBlank()) {
                builder.addRole(js.getString());
            }
        }

        String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : "<unknown>";
        LOG.fine(() -> "OpenShiftGroupsAugmentor: added groups as roles for " + principal + ": " + groups.toString());

        return Uni.createFrom().item(builder.build());
    }
}

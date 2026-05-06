package com.ep18.couriersync.backend.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts Keycloak realm and client roles as Spring Security authorities.
 *
 * Examples:
 * - realm_access.roles: ["admin"]
 * - resource_access.{client}.roles: ["operator"]
 * - "admin" becomes "ROLE_ADMIN"
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> rawRoles = new LinkedHashSet<>();

        addRolesFromAccessMap(rawRoles, jwt.getClaim(REALM_ACCESS));
        addRolesFromResources(rawRoles, jwt.getClaim(RESOURCE_ACCESS));

        return rawRoles.stream()
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(this::normalizeRole)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void addRolesFromResources(Set<String> rawRoles, Object resourceAccess) {
        if (!(resourceAccess instanceof Map<?, ?> resources)) {
            return;
        }

        resources.values()
                .forEach(resource -> addRolesFromAccessMap(rawRoles, resource));
    }

    private void addRolesFromAccessMap(Set<String> rawRoles, Object access) {
        if (!(access instanceof Map<?, ?> accessMap)) {
            return;
        }

        addRoles(rawRoles, accessMap.get(ROLES));
    }

    private void addRoles(Set<String> rawRoles, Object rolesObject) {
        if (!(rolesObject instanceof Collection<?> roles)) {
            return;
        }

        roles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .forEach(rawRoles::add);
    }

    private String normalizeRole(String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        return normalized.startsWith(ROLE_PREFIX) ? normalized : ROLE_PREFIX + normalized;
    }
}

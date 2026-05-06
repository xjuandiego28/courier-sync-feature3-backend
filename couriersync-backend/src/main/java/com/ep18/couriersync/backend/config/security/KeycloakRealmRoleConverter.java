package com.ep18.couriersync.backend.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return Stream.concat(
                        rolesFromAccessMap(jwt.getClaim(REALM_ACCESS)),
                        rolesFromResources(jwt.getClaim(RESOURCE_ACCESS)))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(this::normalizeRole)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Stream<String> rolesFromResources(Object resourceAccess) {
        return mapValues(resourceAccess)
                .map(Map.Entry::getValue)
                .flatMap(this::rolesFromAccessMap);
    }

    private Stream<String> rolesFromAccessMap(Object access) {
        return mapValues(access)
                .filter(rolesEntry -> ROLES.equals(rolesEntry.getKey()))
                .flatMap(rolesEntry -> roleValues(rolesEntry.getValue()));
    }

    private Stream<Map.Entry<?, ?>> mapValues(Object value) {
        return asMap(value)
                .stream()
                .flatMap(map -> map.entrySet().stream());
    }

    private Stream<String> roleValues(Object rolesObject) {
        return asCollection(rolesObject)
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(Object::toString);
    }

    private Optional<Map<?, ?>> asMap(Object value) {
        return Optional.ofNullable(value)
                .filter(Map.class::isInstance)
                .map(candidate -> (Map<?, ?>) candidate);
    }

    private Optional<Collection<?>> asCollection(Object value) {
        return Optional.ofNullable(value)
                .filter(Collection.class::isInstance)
                .map(candidate -> (Collection<?>) candidate);
    }

    private String normalizeRole(String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        return Optional.of(normalized)
                .filter(value -> value.startsWith(ROLE_PREFIX))
                .orElse(ROLE_PREFIX + normalized);
    }
}

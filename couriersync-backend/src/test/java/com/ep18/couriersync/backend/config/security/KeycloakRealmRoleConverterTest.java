package com.ep18.couriersync.backend.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void convertsRealmAndClientRolesToNormalizedAuthorities() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("admin", "ROLE_support", " operator ")),
                "resource_access", Map.of(
                        "courier-sync", Map.of("roles", List.of("seller", "admin")),
                        "account", Map.of("roles", List.of("viewer"))
                )
        ));

        var authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting("authority")
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN",
                        "ROLE_SUPPORT",
                        "ROLE_OPERATOR",
                        "ROLE_SELLER",
                        "ROLE_VIEWER"
                );
    }

    @Test
    void ignoresMissingInvalidAndNullRoles() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", Arrays.asList("admin", null, " ")),
                "resource_access", "invalid"
        ));

        var authorities = converter.convert(jwt);

        assertThat(authorities).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T01:00:00Z"),
                Map.of("alg", "none"),
                claims
        );
    }
}

package com.ep18.couriersync.backend.config.security;

import java.util.Optional;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public AudienceValidator(String audience) { this.audience = audience; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return Optional.ofNullable(jwt.getAudience())
                .filter(aud -> aud.contains(audience))
                .map(aud -> OAuth2TokenValidatorResult.success())
                .orElseGet(this::failure);
    }

    private OAuth2TokenValidatorResult failure() {
        OAuth2Error err = new OAuth2Error("invalid_token", "Missing required audience: " + audience, null);
        return OAuth2TokenValidatorResult.failure(err);
    }
}

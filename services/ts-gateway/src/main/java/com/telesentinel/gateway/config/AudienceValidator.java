package com.telesentinel.gateway.config;

import java.util.Collection;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;

/** Rejects tokens issued for a different API. Signature and issuer alone do not prove the token is meant for us. */
public final class AudienceValidator {

    private AudienceValidator() { }

    public static OAuth2TokenValidator<Jwt> of(String audience) {
        return new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(audience));
    }
}

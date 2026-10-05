package com.telesentinel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.telesentinel.gateway.config.AudienceValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "none").claim("sub", "x");
    }

    @Test
    void acceptsTokenIssuedForThisApi() {
        Jwt token = jwt().claim("aud", List.of("api://telesentinel")).build();
        assertFalse(AudienceValidator.of("api://telesentinel").validate(token).hasErrors());
    }

    @Test
    void rejectsTokenForAnotherApi() {
        Jwt token = jwt().claim("aud", List.of("api://something-else")).build();
        assertTrue(AudienceValidator.of("api://telesentinel").validate(token).hasErrors());
    }

    @Test
    void rejectsTokenWithoutAudience() {
        assertTrue(AudienceValidator.of("api://telesentinel").validate(jwt().build()).hasErrors());
    }
}

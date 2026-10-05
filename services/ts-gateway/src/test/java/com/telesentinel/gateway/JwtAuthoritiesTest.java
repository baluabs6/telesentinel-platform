package com.telesentinel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.telesentinel.gateway.config.JwtAuthorities;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthoritiesTest {

    private Set<String> names(Jwt jwt) {
        return JwtAuthorities.from(jwt).stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void userTokenScopesAreMapped() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("scp", "noc.read fraud.write").build();
        assertEquals(Set.of("SCOPE_noc.read", "SCOPE_fraud.write"), names(jwt));
    }

    @Test
    void machineTokenRolesAreMappedToTheSameAuthorityNames() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("roles", List.of("telemetry.write")).build();
        assertEquals(Set.of("SCOPE_telemetry.write"), names(jwt));
    }

    @Test
    void tokenWithNoScopesGetsNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "x").build();
        assertTrue(names(jwt).isEmpty());
    }
}

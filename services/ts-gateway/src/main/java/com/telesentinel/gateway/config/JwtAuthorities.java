package com.telesentinel.gateway.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps token claims to authorities named SCOPE_x:
 *  - "scp" / "scope": delegated (user) tokens, space separated or a list
 *  - "roles": application (client-credentials) tokens, e.g. Entra ID app roles used by an NMS or mediation system
 */
public final class JwtAuthorities {

    private JwtAuthorities() { }

    public static Collection<GrantedAuthority> from(Jwt jwt) {
        Set<GrantedAuthority> out = new LinkedHashSet<>();
        addScopes(jwt.getClaim("scp"), out);
        addScopes(jwt.getClaim("scope"), out);
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(r -> out.add(new SimpleGrantedAuthority("SCOPE_" + r)));
        }
        return out;
    }

    private static void addScopes(Object claim, Set<GrantedAuthority> out) {
        if (claim instanceof String s) {
            for (String part : s.trim().split("\\s+")) {
                if (!part.isEmpty()) {
                    out.add(new SimpleGrantedAuthority("SCOPE_" + part));
                }
            }
        } else if (claim instanceof Collection<?> c) {
            c.forEach(o -> out.add(new SimpleGrantedAuthority("SCOPE_" + o)));
        }
    }
}

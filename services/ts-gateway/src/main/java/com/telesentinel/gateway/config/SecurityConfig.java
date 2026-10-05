package com.telesentinel.gateway.config;

import java.security.Principal;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT resource server. Names expected from your identity provider (as scopes OR app roles):
 *   telemetry.write  ingestion of alarms and CDRs
 *   noc.read         everything else
 *   fraud.write      change fraud alert status
 *   incidents.write  resolve incidents
 *   knowledge.write  add runbooks
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "telesentinel.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityWebFilterChain secured(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)   // stateless bearer-token API
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/alarms", "/api/v1/cdrs", "/api/v1/cdrs/batch")
                            .hasAuthority("SCOPE_telemetry.write")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/fraud/**").hasAuthority("SCOPE_fraud.write")
                        .pathMatchers(HttpMethod.POST, "/api/v1/incidents/*/resolve").hasAuthority("SCOPE_incidents.write")
                        .pathMatchers("/api/v1/knowledge/**").hasAuthority("SCOPE_knowledge.write")
                        .anyExchange().hasAuthority("SCOPE_noc.read"))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "telesentinel.security.enabled", havingValue = "false")
    SecurityWebFilterChain open(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    /** Validates signature, issuer AND audience. Fails at startup if the audience is not configured. */
    @Bean
    @ConditionalOnProperty(name = "telesentinel.security.enabled", havingValue = "true", matchIfMissing = true)
    ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${telesentinel.security.audience:}") String audience) {
        Assert.hasText(audience, "telesentinel.security.audience (JWT_AUDIENCE) must be set when security is enabled");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), AudienceValidator.of(audience)));
        return decoder;
    }

    static ReactiveJwtAuthenticationConverterAdapter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(JwtAuthorities::from);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /** Rate-limit per authenticated client, falling back to the remote address. */
    @Bean
    KeyResolver clientKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> Optional
                        .ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(a -> a.getAddress().getHostAddress())
                        .orElse("anonymous")));
    }
}

package com.tushar27x.insightshub.security;

import com.tushar27x.insightshub.error.ProblemDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless API: every request authenticates with {@code Authorization: Bearer <jwt>}.
 * No session and no cookies carry auth, so CSRF protection has nothing to protect.
 * (The short-lived {@code oauth_state} cookie only guards the login round trip.)
 * <p>
 * 401 and 403 keep Spring's {@code WWW-Authenticate} header and add the API's {@code ProblemDetail}
 * body. The detail is fixed: it never repeats why the token failed or any part of it.
 */
@Configuration
public class SecurityConfig {

    static final String UNAUTHORIZED = "Missing, invalid or expired token; log in again";
    static final String FORBIDDEN = "This token can't access this resource";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = unauthorized(jsonMapper);
        AccessDeniedHandler forbidden = forbidden(jsonMapper);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error: let the error response through, or every failure on a public path becomes a 401
                        .requestMatchers("/api/auth/**", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    static AuthenticationEntryPoint unauthorized(JsonMapper jsonMapper) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception); // status + WWW-Authenticate (RFC 6750)
            ProblemDetails.write(response, ProblemDetails.of(HttpStatus.UNAUTHORIZED, UNAUTHORIZED, request), jsonMapper);
        };
    }

    static AccessDeniedHandler forbidden(JsonMapper jsonMapper) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, exception) -> {
            bearer.handle(request, response, exception);
            ProblemDetails.write(response, ProblemDetails.of(HttpStatus.FORBIDDEN, FORBIDDEN, request), jsonMapper);
        };
    }
}

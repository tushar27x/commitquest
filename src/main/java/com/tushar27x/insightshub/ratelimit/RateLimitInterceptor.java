package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.error.ProblemDetails;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.ratelimit.RateLimiter.Decision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Applies {@link RateLimit} to controller methods. Runs after Spring Security's filters, so a
 * request with a bad JWT is rejected (401) before it counts, and the user is already known.
 * <p>
 * Over the limit: 429 with {@code Retry-After} and a {@link ProblemDetail} body (RFC 9457, the
 * format of every error, see {@link ProblemDetails}). Every limited response carries
 * {@code X-RateLimit-Limit} and {@code X-RateLimit-Remaining}.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final JsonMapper jsonMapper;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties, JsonMapper jsonMapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!properties.enabled() || !(handler instanceof HandlerMethod method)) {
            return true; // static resources, /error, ...
        }
        RateLimit limit = method.getMethodAnnotation(RateLimit.class);
        if (limit == null) {
            return true;
        }

        Decision decision = limiter.tryAcquire(key(limit, request), properties.policy(limit.policy()));
        response.setHeader(LIMIT_HEADER, Integer.toString(decision.limit()));
        response.setHeader(REMAINING_HEADER, Integer.toString(decision.remaining()));
        if (decision.allowed()) {
            return true;
        }

        long seconds = ProblemDetails.retryAfterSeconds(decision.retryAfter());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        ProblemDetails.write(response, ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded; try again in " + seconds + " s", request), jsonMapper);
        return false;
    }

    /**
     * {@code <policy>:ip:<address>} or {@code <policy>:user:<githubId>}. The IP is the connection's
     * address: {@code X-Forwarded-For} is not trusted, because any client can set it to dodge the limit.
     * Behind a real proxy (Phase 14), {@code server.forward-headers-strategy} makes Spring resolve it safely.
     */
    static String key(RateLimit limit, HttpServletRequest request) {
        if (limit.key() == KeyType.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                return limit.policy() + ":user:" + jwt.getName(); // JWT subject = GitHub id
            }
        }
        return limit.policy() + ":ip:" + request.getRemoteAddr(); // also the fallback if no user is known
    }
}

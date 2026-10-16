package com.tushar27x.insightshub.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one error format (RFC 9457, {@code docs/api/contract.md}):
 * {@code {type, title, status, detail, instance}} as {@code application/problem+json}.
 * Controllers get it from {@link ApiExceptionHandler}; code outside Spring MVC (security filters,
 * the rate-limit interceptor) writes it with {@link #write}.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /** RFC 9457's default type. Spring 7 leaves {@code type} null, but the contract always sends it. */
    public static final URI BLANK = URI.create("about:blank");

    /** A problem for this request; {@code instance} is the request path (never the query string). */
    public static ProblemDetail of(HttpStatusCode status, String detail, HttpServletRequest request) {
        return complete(ProblemDetail.forStatusAndDetail(status, detail), request.getRequestURI());
    }

    /** Fills in {@code type} and {@code instance} where they are missing. */
    public static ProblemDetail complete(ProblemDetail problem, String path) {
        if (problem.getType() == null) {
            problem.setType(BLANK);
        }
        if (problem.getInstance() == null) {
            problem.setInstance(instance(path));
        }
        return problem;
    }

    /** The path as a URI, or null if it isn't a valid one (the error must still be sent). */
    public static URI instance(String path) {
        try {
            return path == null ? null : URI.create(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Writes the problem as the response body, with its status and content type. */
    public static void write(HttpServletResponse response, ProblemDetail problem, JsonMapper jsonMapper)
            throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), asMap(problem));
    }

    /**
     * Same keys as Spring MVC writes, extensions flattened. Done by hand because a plain
     * {@link JsonMapper} lacks the mixin Spring's message converter uses for {@link ProblemDetail}.
     */
    static Map<String, Object> asMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", (problem.getType() == null ? BLANK : problem.getType()).toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", problem.getInstance() == null ? null : problem.getInstance().toString());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }

    /** Whole seconds for {@code Retry-After}: rounded up and at least 1, since 0 would invite an instant retry. */
    public static long retryAfterSeconds(Duration wait) {
        return Math.max(1, (wait.toMillis() + 999) / 1000);
    }
}

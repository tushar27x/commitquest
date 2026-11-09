package com.tushar27x.insightshub.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.time.Duration;

/** 429 with {@code Retry-After}: a cooldown (sync, class switch) or a busy shared resource. */
public class TooManyRequestsException extends ErrorResponseException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String detail, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail), null);
        this.retryAfterSeconds = ProblemDetails.retryAfterSeconds(retryAfter);
        getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    }

    /** B11: with its own {@code title}, so a client can tell a lock from the plain rate limit. */
    public TooManyRequestsException(String title, String detail, Duration retryAfter) {
        this(detail, retryAfter);
        getBody().setTitle(title);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

package com.tushar27x.insightshub.error;

import com.tushar27x.insightshub.github.GitHubApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Every exception a controller throws becomes a {@link ProblemDetail} here, in one place.
 * <ul>
 *   <li>Spring MVC's own exceptions (bad input, unknown path, wrong method) and every
 *       {@code ErrorResponse} ({@code ResponseStatusException}, {@link TooManyRequestsException}):
 *       handled by {@link ResponseEntityExceptionHandler}, headers included.</li>
 *   <li>Validation failures: 400 with an {@code errors} list of {@code {field, message}}; field names
 *       are snake_case, like every key in the API, not the Java names.</li>
 *   <li>{@link GitHubApiException}: 502.</li>
 *   <li>Anything else: 500 with a generic detail. Exception messages can carry internal details
 *       (SQL, logins, upstream errors), so they are logged, never returned.</li>
 * </ul>
 * Errors raised before a controller runs (missing JWT, rate limit) are written by
 * {@code SecurityConfig} and {@code RateLimitInterceptor} with {@link ProblemDetails}; whatever reaches
 * the servlet container goes through {@link ProblemErrorController}. Same shape everywhere.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS = "errors";
    static final String INVALID_REQUEST = "The request is invalid; see 'errors'";

    @ExceptionHandler(GitHubApiException.class)
    ResponseEntity<ProblemDetail> gitHubUnavailable(GitHubApiException e, HttpServletRequest request) {
        log.warn("{} {}: GitHub failed: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "GitHub is unavailable; try again later", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        log.error("{} {} failed", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side; try again later", request);
    }

    /** {@code @Valid} request bodies. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldMessage> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(new FieldMessage(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new FieldMessage(error.getObjectName(), error.getDefaultMessage())));
        return invalid(ex, errors, headers, status, request);
    }

    /** Constraints on {@code @RequestParam}, {@code @PathVariable}, ... (method validation). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldMessage> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fieldError ? name + "." + fieldError.getField() : name;
                errors.add(new FieldMessage(field, error.getDefaultMessage()));
            }
        });
        return invalid(ex, errors, headers, status, request);
    }

    private ResponseEntity<Object> invalid(Exception ex, List<FieldMessage> errors, HttpHeaders headers,
                                           HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, INVALID_REQUEST);
        problem.setProperty(ERRORS, errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Every response from the base class passes here: fill in {@code type} and {@code instance}. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem && request instanceof ServletWebRequest servlet) {
            ProblemDetails.complete(problem, servlet.getRequest().getRequestURI());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** {@code restDays} → {@code rest_days}, per segment of a path like {@code week[0].restDays}. */
    static String jsonName(String field) {
        if (field == null) {
            return null;
        }
        StringBuilder name = new StringBuilder(field.length() + 4);
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && Character.isLetterOrDigit(field.charAt(i - 1))) {
                    name.append('_');
                }
                name.append(Character.toLowerCase(c));
            } else {
                name.append(c);
            }
        }
        return name.toString();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ProblemDetails.of(status, detail, request));
    }

    /** One entry of {@code errors}. */
    public record FieldMessage(String field, String message) {
        public FieldMessage {
            field = jsonName(field);
        }
    }
}

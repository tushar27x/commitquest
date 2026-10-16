package com.tushar27x.insightshub.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's {@code BasicErrorController} on {@code /error}, where the servlet container sends
 * errors that never reached a controller (an exception in a filter, a request Tomcat rejects).
 * Answers with the same {@link ProblemDetail} as everything else, and never with the exception's
 * message: Boot's error page would include it when {@code server.error.include-message} is on.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = status(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        String path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri ? uri : null;
        ProblemDetail problem = ProblemDetails.complete(ProblemDetail.forStatusAndDetail(status, detail(status)), path);
        return ResponseEntity.status(status).body(problem);
    }

    private static HttpStatus status(Object code) {
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status == null || !status.isError() ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }

    private static String detail(HttpStatus status) {
        return status.is5xxServerError()
                ? "Something went wrong on our side; try again later"
                : status.getReasonPhrase();
    }
}

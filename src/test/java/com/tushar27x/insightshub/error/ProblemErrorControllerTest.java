package com.tushar27x.insightshub.error;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemErrorControllerTest {

    private final ProblemErrorController controller = new ProblemErrorController();

    @Test
    void containerErrorBecomesAProblemForTheOriginalPath() {
        ResponseEntity<ProblemDetail> response = controller.error(forward(400, "/api/user/insights"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).isEqualTo("Bad Request");
        assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/api/user/insights"));
    }

    @Test
    void serverErrorsGetAGenericDetailOnly() {
        MockHttpServletRequest request = forward(500, "/api/user/sync");
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "Connection to db.neon.tech refused");

        ProblemDetail problem = controller.error(request).getBody();

        assertThat(problem.getDetail()).isEqualTo("Something went wrong on our side; try again later");
    }

    @Test
    void missingOrNonErrorStatusIs500() {
        assertThat(controller.error(new MockHttpServletRequest()).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.error(forward(200, "/x")).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static MockHttpServletRequest forward(int status, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        return request;
    }
}

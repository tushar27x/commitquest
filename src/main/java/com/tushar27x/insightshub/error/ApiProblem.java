package com.tushar27x.insightshub.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The error body as the API docs show it (B9). Documentation only: the API writes {@code ProblemDetail}
 * ({@link ApiExceptionHandler}, {@link ProblemDetails}), whose Java shape (a {@code properties} map) differs from
 * the JSON it produces. This record is that JSON.
 */
@Schema(name = "Problem", description = "RFC 9457 problem, sent as application/problem+json on every error. "
        + "Tell errors apart by status and endpoint, never by type, title or detail (one exception: the two 404s "
        + "of GET /api/user/rank, told apart by title).")
public record ApiProblem(
        @Schema(example = "about:blank", requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(description = "The HTTP reason phrase", example = "Not Found",
                requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(example = "404", requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(description = "For logs; the UI branches on the status", example = "No hero yet for this user; sync first",
                requiredMode = Schema.RequiredMode.REQUIRED) String detail,
        @Schema(description = "The request path, no query string", example = "/api/user/hero",
                requiredMode = Schema.RequiredMode.REQUIRED) String instance,
        @Schema(description = "Validation 400s only: one entry per invalid field, with its snake_case JSON name")
        List<ApiExceptionHandler.FieldMessage> errors) {
}

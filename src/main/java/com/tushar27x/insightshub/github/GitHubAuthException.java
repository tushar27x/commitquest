package com.tushar27x.insightshub.github;

import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * GitHub rejected the token (HTTP 401): revoked, expired or invalid.
 * Separate from {@link GitHubApiException} so the scheduled sync can mark the user
 * {@code needs_reauth} instead of retrying.
 * <p>
 * B43: carries what GitHub support needs to trace the request, never the token: GitHub's
 * {@code X-GitHub-Request-Id} and the {@code message} of its error body (e.g. "Bad credentials").
 */
public class GitHubAuthException extends GitHubApiException {

    static final String REQUEST_ID_HEADER = "X-GitHub-Request-Id";
    /** GitHub's 401 body is a small JSON object; anything longer isn't what we're looking for. */
    static final int MAX_BODY_BYTES = 4096;
    static final int MAX_MESSAGE_CHARS = 200;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String requestId;
    private final String gitHubMessage;

    public GitHubAuthException(String message) {
        this(message, null, null);
    }

    public GitHubAuthException(String message, String requestId, String gitHubMessage) {
        super(message + " [GitHub request id " + orUnknown(requestId) + ", message \"" + orUnknown(gitHubMessage) + "\"]");
        this.requestId = requestId;
        this.gitHubMessage = gitHubMessage;
    }

    /** From a 401 response: its request id header and the {@code message} of its JSON body. */
    public static GitHubAuthException of(String what, ClientHttpResponse response) {
        return new GitHubAuthException("GitHub rejected the token for " + what + " (HTTP 401)",
                clean(response.getHeaders().getFirst(REQUEST_ID_HEADER)), message(response));
    }

    /** GitHub's request id, or null if the response had none. */
    public String requestId() {
        return requestId;
    }

    /** GitHub's error message, or null if the body had none. */
    public String gitHubMessage() {
        return gitHubMessage;
    }

    static String message(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            String text = new String(body.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
            JsonNode node = JSON.readTree(text);
            JsonNode message = node == null ? null : node.get("message");
            return message != null && message.isString() ? clean(message.stringValue()) : null;
        } catch (IOException | RuntimeException e) {
            return null; // not JSON, or no body: the request id still identifies it
        }
    }

    /** One short line: no control characters (log injection), at most {@value #MAX_MESSAGE_CHARS} characters. */
    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String line = value.replaceAll("\\p{Cntrl}", " ").strip();
        return line.length() > MAX_MESSAGE_CHARS ? line.substring(0, MAX_MESSAGE_CHARS) : line;
    }

    private static String orUnknown(String value) {
        return value == null ? "unknown" : value;
    }
}

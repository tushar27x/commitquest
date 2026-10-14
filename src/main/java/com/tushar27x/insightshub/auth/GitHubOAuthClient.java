package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubAuthException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

/**
 * GitHub's OAuth and REST endpoints used at login. The token exchange lives on
 * {@code github.com}, not the API host, so it is called with an absolute {@link URI}.
 * Tokens never appear in exception messages or logs.
 */
@Component
public class GitHubOAuthClient {

    static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    static final URI TOKEN_URL = URI.create("https://github.com/login/oauth/access_token");

    private static final ParameterizedTypeReference<List<GitHubEmail>> EMAILS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient gitHubRestClient;
    private final AuthProperties properties;

    public GitHubOAuthClient(RestClient gitHubRestClient, AuthProperties properties) {
        this.gitHubRestClient = gitHubRestClient;
        this.properties = properties;
    }

    /**
     * Trades the one-time {@code code} for a user access token. GitHub reports a bad code
     * with HTTP 200 and an {@code error} field, not with an error status.
     */
    public String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.githubClientId());
        form.add("client_secret", properties.githubClientSecret());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        TokenResponse response = call("exchange the OAuth code", () -> gitHubRestClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw new GitHubApiException("GitHub token exchange failed: HTTP " + res.getStatusCode().value());
                })
                .body(TokenResponse.class));

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            String error = response == null ? "empty response" : response.error();
            throw new OAuthCodeRejectedException(error == null ? "no access token" : error);
        }
        return response.accessToken();
    }

    /** {@code GET /user}: the stable numeric id plus the (changeable) login. */
    public GitHubAccount fetchAccount(String token) {
        GitHubAccount account = call("fetch the user", () -> get("/user", token).body(GitHubAccount.class));
        if (account == null || account.id() == null || account.login() == null) {
            throw new GitHubApiException("GitHub returned no user id or login");
        }
        return account;
    }

    /**
     * {@code GET /user/emails} (needs {@code user:email}): the primary address, only if verified.
     * GraphQL would only expose the public email, which most users leave empty.
     *
     * @return the address, or {@code null} when there is no verified primary email
     */
    public String fetchPrimaryEmail(String token) {
        List<GitHubEmail> emails = call("fetch emails", () -> get("/user/emails", token).body(EMAILS_TYPE));
        if (emails == null) {
            return null;
        }
        return emails.stream()
                .filter(e -> e.primary() && e.verified())
                .map(GitHubEmail::email)
                .findFirst()
                .orElse(null);
    }

    private RestClient.ResponseSpec get(String path, String token) {
        return gitHubRestClient.get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED), (request, res) -> {
                    throw new GitHubAuthException("GitHub rejected the token for " + path + " (HTTP 401)");
                })
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw new GitHubApiException("GitHub " + path + " failed: HTTP " + res.getStatusCode().value());
                });
    }

    private static <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (GitHubApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GitHubApiException("Could not " + action + " with GitHub", e);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TokenResponse(String accessToken, String scope, String error) {
    }

    public record GitHubAccount(Long id, String login) {
    }

    record GitHubEmail(String email, boolean primary, boolean verified) {
    }

    /** GitHub refused the code: expired, already used, or the redirect URI didn't match. */
    public static class OAuthCodeRejectedException extends GitHubApiException {

        private final String error;

        public OAuthCodeRejectedException(String error) {
            super("GitHub rejected the OAuth code: " + error);
            this.error = error;
        }

        /** GitHub's error code (e.g. {@code bad_verification_code}); safe to show to the user. */
        public String error() {
            return error;
        }
    }
}

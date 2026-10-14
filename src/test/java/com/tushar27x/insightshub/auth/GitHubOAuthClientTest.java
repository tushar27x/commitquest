package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.OAuthCodeRejectedException;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubClientConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Unit tests against a mocked GitHub: no network, no Spring context. */
class GitHubOAuthClientTest {

    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    private MockRestServiceServer github;
    private GitHubOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        github = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = new GitHubClientConfig().gitHubRestClient(builder);
        client = new GitHubOAuthClient(restClient, SecurityTestData.authProperties());
    }

    @Test
    void exchangesCodeForToken() {
        github.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "client-id",
                        "client_secret", "client-secret",
                        "code", "the-code",
                        "redirect_uri", REDIRECT_URI)))
                .andRespond(withSuccess("""
                        {"access_token":"gho_abc","token_type":"bearer","scope":"read:user,user:email"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCode("the-code", REDIRECT_URI)).isEqualTo("gho_abc");
        github.verify();
    }

    @Test
    void rejectedCodeComesBackAs200WithError() {
        github.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("""
                        {"error":"bad_verification_code","error_description":"The code passed is incorrect or expired."}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("used-code", REDIRECT_URI))
                .isInstanceOf(OAuthCodeRejectedException.class)
                .satisfies(e -> assertThat(((OAuthCodeRejectedException) e).error()).isEqualTo("bad_verification_code"));
    }

    @Test
    void tokenEndpointHttpErrorIsApiError() {
        github.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.exchangeCode("code", REDIRECT_URI))
                .isInstanceOf(GitHubApiException.class)
                .isNotInstanceOf(OAuthCodeRejectedException.class)
                .hasMessageContaining("503");
    }

    @Test
    void fetchesAccountWithBearerToken() {
        github.expect(requestTo("https://api.github.com/user"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_abc"))
                .andRespond(withSuccess("""
                        {"id":583231,"login":"octocat","name":"The Octocat","email":null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchAccount("gho_abc")).isEqualTo(new GitHubAccount(583231L, "octocat"));
    }

    @Test
    void revokedTokenIsAuthError() {
        github.expect(requestTo("https://api.github.com/user"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchAccount("revoked"))
                .isInstanceOf(GitHubAuthException.class)
                .hasMessageNotContaining("revoked");
    }

    @Test
    void picksPrimaryVerifiedEmail() {
        github.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("""
                        [
                          {"email":"old@example.com","primary":false,"verified":true,"visibility":null},
                          {"email":"octocat@example.com","primary":true,"verified":true,"visibility":"private"},
                          {"email":"123+octocat@users.noreply.github.com","primary":false,"verified":true,"visibility":null}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchPrimaryEmail("gho_abc")).isEqualTo("octocat@example.com");
    }

    @Test
    void unverifiedPrimaryEmailIsIgnored() {
        github.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("""
                        [{"email":"new@example.com","primary":true,"verified":false,"visibility":"private"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchPrimaryEmail("gho_abc")).isNull();
    }
}

package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.auth.AuthService.LoginResult;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.OAuthCodeRejectedException;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";
    private static final long GITHUB_ID = 583231L;

    private final GitHubOAuthClient gitHub = mock(GitHubOAuthClient.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final TokenCipher cipher = new TokenCipher(SecurityTestData.appProperties());
    private final SyncService syncService = mock(SyncService.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(JwtConfig.encoder(SecurityTestData.JWT_SECRET),
                Clock.systemUTC(), SecurityTestData.authProperties());
        service = new AuthService(gitHub, usersRepository, cipher, jwtService, syncService,
                SecurityTestData.authProperties());

        when(gitHub.exchangeCode("code", REDIRECT_URI)).thenReturn("gho_secret");
        when(gitHub.fetchAccount("gho_secret")).thenReturn(new GitHubAccount(GITHUB_ID, "octocat"));
        when(gitHub.fetchPrimaryEmail("gho_secret")).thenReturn("octocat@example.com");
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void authorizeUriAsksForPublicScopesOnly() {
        URI uri = service.authorizeUri("st4te", REDIRECT_URI);
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        assertThat(uri.toString()).startsWith("https://github.com/login/oauth/authorize?");
        assertThat(params.getFirst("client_id")).isEqualTo("client-id");
        assertThat(params.getFirst("state")).isEqualTo("st4te");
        assertThat(uri.getQuery()).contains("scope=read:user user:email")
                .contains("redirect_uri=" + REDIRECT_URI)
                .doesNotContain("repo");
    }

    @Test
    void newUserIsCreatedWithEncryptedTokenAndEmail() {
        when(usersRepository.findById(GITHUB_ID)).thenReturn(Optional.empty());

        LoginResult result = service.completeLogin("code", REDIRECT_URI);

        Users saved = savedUser();
        assertThat(saved.getGithubId()).isEqualTo(GITHUB_ID);
        assertThat(saved.getLogin()).isEqualTo("octocat");
        assertThat(saved.getEmail()).isEqualTo("octocat@example.com");
        assertThat(saved.getEncryptedGithubToken()).isNotEqualTo("gho_secret");
        assertThat(cipher.decrypt(saved.getEncryptedGithubToken(), GITHUB_ID)).isEqualTo("gho_secret");
        assertThat(result.login()).isEqualTo("octocat");
        assertThat(result.token().value()).isNotBlank();
    }

    @Test
    void existingUserKeepsSettingsAndGetsRenamedAndReauthorized() {
        Users existing = new Users(GITHUB_ID, "old-login");
        existing.setAlertsEnabled(true);
        existing.setNoCommitDays(7);
        existing.setPublicProfile(true);
        existing.setNeedsReauth(true);
        when(usersRepository.findById(GITHUB_ID)).thenReturn(Optional.of(existing));

        service.completeLogin("code", REDIRECT_URI);

        Users saved = savedUser();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getLogin()).isEqualTo("octocat");
        assertThat(saved.isAlertsEnabled()).isTrue();
        assertThat(saved.getNoCommitDays()).isEqualTo(7);
        assertThat(saved.isPublicProfile()).isTrue();
        assertThat(saved.isNeedsReauth()).isFalse();
    }

    @Test
    void loginSyncsStaleInsights() {
        when(usersRepository.findById(GITHUB_ID)).thenReturn(Optional.empty());

        service.completeLogin("code", REDIRECT_URI);

        verify(syncService).syncIfDue(GITHUB_ID);
    }

    @Test
    void failedSyncStillLogsIn() {
        when(usersRepository.findById(GITHUB_ID)).thenReturn(Optional.empty());
        when(syncService.syncIfDue(GITHUB_ID)).thenThrow(new IllegalStateException("GitHub down"));

        LoginResult result = service.completeLogin("code", REDIRECT_URI);

        assertThat(result.token().value()).isNotBlank();
        verify(usersRepository).save(any(Users.class));
    }

    @Test
    void rejectedCodeSavesNothing() {
        when(gitHub.exchangeCode("bad", REDIRECT_URI)).thenThrow(new OAuthCodeRejectedException("bad_verification_code"));

        assertThatThrownBy(() -> service.completeLogin("bad", REDIRECT_URI))
                .isInstanceOf(OAuthCodeRejectedException.class);
        verify(usersRepository, never()).save(any());
        verify(syncService, never()).syncIfDue(anyLong());
    }

    private Users savedUser() {
        ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
        verify(usersRepository).save(captor.capture());
        return captor.getValue();
    }
}

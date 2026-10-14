package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.JwtService.IssuedToken;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.SyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * The login round trip: build GitHub's authorize URL, then turn the returned code into
 * a stored (encrypted) GitHub token, a sync of stale insights, and one of our JWTs.
 */
@Slf4j
@Service
public class AuthService {

    private final GitHubOAuthClient gitHub;
    private final UsersRepository usersRepository;
    private final TokenCipher tokenCipher;
    private final JwtService jwtService;
    private final SyncService syncService;
    private final AuthProperties properties;

    public AuthService(GitHubOAuthClient gitHub, UsersRepository usersRepository, TokenCipher tokenCipher,
                       JwtService jwtService, SyncService syncService, AuthProperties properties) {
        this.gitHub = gitHub;
        this.usersRepository = usersRepository;
        this.tokenCipher = tokenCipher;
        this.jwtService = jwtService;
        this.syncService = syncService;
        this.properties = properties;
    }

    public URI authorizeUri(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(GitHubOAuthClient.AUTHORIZE_URL)
                .queryParam("client_id", properties.githubClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", properties.scopes())
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    /**
     * Exchanges the code, then creates the user or updates the existing row. Existing rows are
     * loaded first so their settings (alerts, leaderboard opt-in, ...) survive the save (B18).
     * The GitHub calls happen before the save, so no transaction is held open across them.
     */
    public LoginResult completeLogin(String code, String redirectUri) {
        String githubToken = gitHub.exchangeCode(code, redirectUri);
        GitHubAccount account = gitHub.fetchAccount(githubToken);
        String email = gitHub.fetchPrimaryEmail(githubToken);

        Users user = usersRepository.findById(account.id())
                .orElseGet(() -> new Users(account.id(), account.login()));
        boolean isNew = user.getCreatedAt() == null;
        user.setLogin(account.login()); // logins can be renamed; the id is what stays
        user.setEmail(email);
        user.setEncryptedGithubToken(tokenCipher.encrypt(githubToken, account.id()));
        user.setNeedsReauth(false); // a fresh token clears a revoked one (Phase 10)
        usersRepository.save(user);
        log.info("Login for '{}' ({}): {} user, verified email {}",
                account.login(), account.id(), isNew ? "new" : "existing", email == null ? "missing" : "stored");

        syncQuietly(account.id(), account.login());
        IssuedToken token = jwtService.issue(account.id(), account.login());
        return new LoginResult(account.login(), token);
    }

    /**
     * Like v1, a failed sync must not fail the login: the user still gets a token, and the next login
     * (or Phase 10's daily job) tries again. It runs before the response, like v1, so a fresh user's
     * first {@code /api/user/insights} already has data.
     */
    private void syncQuietly(long githubId, String login) {
        try {
            syncService.syncIfStale(githubId);
        } catch (RuntimeException e) {
            log.error("Sync at login failed for '{}'; login continues", login, e);
        }
    }

    public record LoginResult(String login, IssuedToken token) {
    }
}

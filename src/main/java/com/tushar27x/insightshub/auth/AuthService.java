package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.OAuthToken;
import com.tushar27x.insightshub.entity.GitHubScopes;
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
import java.time.Clock;

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
    private final Clock clock;

    public AuthService(GitHubOAuthClient gitHub, UsersRepository usersRepository, TokenCipher tokenCipher,
                       JwtService jwtService, SyncService syncService, AuthProperties properties, Clock clock) {
        this.gitHub = gitHub;
        this.usersRepository = usersRepository;
        this.tokenCipher = tokenCipher;
        this.jwtService = jwtService;
        this.syncService = syncService;
        this.properties = properties;
        this.clock = clock;
    }

    public URI authorizeUri(String state, String redirectUri) {
        return authorizeUri(state, redirectUri, false);
    }

    /** B10: {@code includePrivate} adds the {@code repo} scope (GitHub has no read-only private scope). */
    public URI authorizeUri(String state, String redirectUri, boolean includePrivate) {
        return UriComponentsBuilder.fromUriString(GitHubOAuthClient.AUTHORIZE_URL)
                .queryParam("client_id", properties.githubClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", includePrivate ? properties.scopes() + " " + GitHubScopes.REPO : properties.scopes())
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
        return completeLogin(code, redirectUri, false);
    }

    /**
     * B10: records the scopes GitHub granted (replacing the previous list, so a plain login narrows it). A login that
     * asked for {@code private} and got {@code repo} turns {@code include_private} on; otherwise the choice is never
     * touched. If {@code repo} was gained or lost, the login sync skips the 10-minute cooldown, so the insights match
     * the new grant at once.
     */
    public LoginResult completeLogin(String code, String redirectUri, boolean privateIntent) {
        OAuthToken grant = gitHub.exchangeCode(code, redirectUri);
        String githubToken = grant.accessToken();
        GitHubAccount account = gitHub.fetchAccount(githubToken);
        String email = gitHub.fetchPrimaryEmail(githubToken);

        Users user = usersRepository.findById(account.id())
                .orElseGet(() -> new Users(account.id(), account.login()));
        boolean isNew = user.getCreatedAt() == null;
        boolean hadRepo = user.hasRepoScope();
        String scopes = GitHubScopes.normalize(grant.scope());
        boolean hasRepo = GitHubScopes.hasRepo(scopes);
        user.setGithubScopes(scopes);
        if (privateIntent && hasRepo) {
            user.setIncludePrivate(true);
        }
        user.setLogin(account.login()); // logins can be renamed; the id is what stays
        user.setEmail(email);
        user.replaceGithubToken(tokenCipher.encrypt(githubToken, account.id()), clock.instant()); // B43: its age
        user.setNeedsReauth(false); // a fresh token clears a revoked one (Phase 10)
        usersRepository.save(user);
        log.info("Login for '{}' ({}): {} user, verified email {}, private repos {} (repo scope {})",
                account.login(), account.id(), isNew ? "new" : "existing", email == null ? "missing" : "stored",
                user.isIncludePrivate() ? "on" : "off", hasRepo ? "granted" : "not granted");

        syncQuietly(account.id(), account.login(), hadRepo != hasRepo);
        IssuedToken token = jwtService.issue(account.id(), account.login());
        return new LoginResult(account.login(), token);
    }

    /**
     * Like v1, a failed sync must not fail the login: the user still gets a token and can refresh later.
     * It runs before the response, like v1, so a new user's first {@code /api/user/insights} already
     * has data. Subject to the same 10-minute cooldown as {@code POST /api/user/sync}.
     */
    private void syncQuietly(long githubId, String login, boolean scopeChanged) {
        try {
            if (scopeChanged) {
                syncService.sync(githubId); // B10 Q5: the grant changed, so no cooldown
            } else {
                syncService.syncIfDue(githubId);
            }
        } catch (RuntimeException e) {
            log.error("Sync at login failed for '{}'; login continues", login, e);
        }
    }

    public record LoginResult(String login, IssuedToken token) {
    }
}

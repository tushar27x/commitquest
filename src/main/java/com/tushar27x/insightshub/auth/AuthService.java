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
import com.tushar27x.insightshub.user.PrivateProperties;
import com.tushar27x.insightshub.user.PrivateSwitch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

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
    private final PrivateProperties privateProperties;
    private final Clock clock;
    /** #71: logins by one-time code, so a replayed callback gets the first one's result. */
    private final ConcurrentHashMap<String, Replayable> recentLogins = new ConcurrentHashMap<>();

    public AuthService(GitHubOAuthClient gitHub, UsersRepository usersRepository, TokenCipher tokenCipher,
                       JwtService jwtService, SyncService syncService, AuthProperties properties,
                       PrivateProperties privateProperties, Clock clock) {
        this.privateProperties = privateProperties;
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
     * B10/B11: records the scopes GitHub granted, always (they replace the previous list). What the login may change
     * about private access is decided here, where the user is known: locked ({@code private_changed_at} + the cooldown
     * in the future) the {@code private} intent is ignored; otherwise an intent with {@code repo} granted turns
     * {@code include_private} on; no intent never changes it, and cancelled consent or an unticked {@code repo}
     * change nothing. When that makes private access <b>effective</b> or not ({@code include_private} and
     * {@code repo}), the login's sync is a full resync without the cooldown ({@code private_resync_due} is set
     * first, so a failed sync is repeated by the next job). Gaining it starts the lock unless already locked; losing
     * the scope is not a user change and never locks.
     */
    public LoginResult completeLogin(String code, String redirectUri, boolean privateIntent) {
        Instant now = clock.instant();
        recentLogins.values().removeIf(r -> r.done && r.expiresAt.isBefore(now));
        String key = code + '\n' + redirectUri + '\n' + privateIntent;
        CompletableFuture<LoginResult> mine = new CompletableFuture<>();
        Replayable first = recentLogins.putIfAbsent(key, new Replayable(mine, now.plus(REPLAY_WINDOW)));
        if (first != null) {
            log.info("Callback replayed for a code that is already being (or was just) used; sharing the first login's result");
            try {
                return first.result.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException re ? re : e;
            }
        }
        try {
            LoginResult result = doCompleteLogin(code, redirectUri, privateIntent);
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            recentLogins.remove(key); // a failed code is not remembered: the next attempt gets GitHub's real answer
            mine.completeExceptionally(e);
            throw e;
        } finally {
            recentLogins.computeIfPresent(key, (k, r) -> r.markDone());
        }
    }

    /**
     * #71: GitHub's code is single-use. The callback can take a minute (the first sync runs before it answers) and
     * a browser may replay it meanwhile, or when the response never arrived; the replay used to get
     * {@code bad_verification_code}. A replay is only reachable with the same state cookie, so it is the same user.
     */
    private static final Duration REPLAY_WINDOW = Duration.ofMinutes(2);

    private static final class Replayable {
        final CompletableFuture<LoginResult> result;
        final Instant expiresAt;
        volatile boolean done;

        Replayable(CompletableFuture<LoginResult> result, Instant expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }

        Replayable markDone() {
            done = true;
            return this;
        }
    }

    private LoginResult doCompleteLogin(String code, String redirectUri, boolean privateIntent) {
        OAuthToken grant = gitHub.exchangeCode(code, redirectUri);
        String githubToken = grant.accessToken();
        GitHubAccount account = gitHub.fetchAccount(githubToken);
        String email = gitHub.fetchPrimaryEmail(githubToken);

        Users user = usersRepository.findById(account.id())
                .orElseGet(() -> new Users(account.id(), account.login()));
        boolean isNew = user.getCreatedAt() == null;
        Instant now = clock.instant();
        boolean effectiveBefore = user.readsPrivate();
        boolean locked = PrivateSwitch.lockedUntil(user.getPrivateChangedAt(), now,
                privateProperties.changeCooldown()) != null;
        String scopes = GitHubScopes.normalize(grant.scope());
        boolean hasRepo = GitHubScopes.hasRepo(scopes);
        user.setGithubScopes(scopes); // always the truth, locked or not
        if (privateIntent && hasRepo && !locked) {
            user.setIncludePrivate(true);
        }
        boolean effectiveChanged = effectiveBefore != user.readsPrivate();
        if (effectiveChanged) {
            user.setPrivateResyncDue(true); // paid by the sync below, or by the next job if that fails
            if (user.readsPrivate() && !locked) {
                user.setPrivateChangedAt(now.truncatedTo(ChronoUnit.SECONDS));
            } // losing the scope is not a user change: no lock, and an existing one stays
        }
        user.setLogin(account.login()); // logins can be renamed; the id is what stays
        user.setEmail(email);
        user.replaceGithubToken(tokenCipher.encrypt(githubToken, account.id()), now); // B43: its age
        user.setNeedsReauth(false); // a fresh token clears a revoked one (Phase 10)
        usersRepository.save(user);
        log.info("Login for '{}' ({}): {} user, verified email {}, private repos {} (repo scope {}){}",
                account.login(), account.id(), isNew ? "new" : "existing", email == null ? "missing" : "stored",
                user.isIncludePrivate() ? "on" : "off", hasRepo ? "granted" : "not granted",
                privateIntent && locked ? ", private intent ignored: locked" : "");

        syncQuietly(account.id(), account.login(), effectiveChanged);
        IssuedToken token = jwtService.issue(account.id(), account.login());
        return new LoginResult(account.login(), token);
    }

    /**
     * Like v1, a failed sync must not fail the login: the user still gets a token and can refresh later.
     * It runs before the response, like v1, so a new user's first {@code /api/user/insights} already
     * has data. Subject to the same 10-minute cooldown as {@code POST /api/user/sync}.
     */
    private void syncQuietly(long githubId, String login, boolean effectiveChanged) {
        try {
            if (effectiveChanged) {
                syncService.sync(githubId); // B11: a full resync (private_resync_due), no cooldown
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

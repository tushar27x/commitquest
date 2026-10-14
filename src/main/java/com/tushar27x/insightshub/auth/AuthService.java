package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.JwtService.IssuedToken;
import com.tushar27x.insightshub.security.TokenCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * The login round trip: build GitHub's authorize URL, then turn the returned code into
 * a stored (encrypted) GitHub token and one of our JWTs.
 * <p>
 * Sync of the user's insights is Phase 7 ({@code SyncService}); login only records identity.
 */
@Slf4j
@Service
public class AuthService {

    private final GitHubOAuthClient gitHub;
    private final UsersRepository usersRepository;
    private final TokenCipher tokenCipher;
    private final JwtService jwtService;
    private final AuthProperties properties;

    public AuthService(GitHubOAuthClient gitHub, UsersRepository usersRepository, TokenCipher tokenCipher,
                       JwtService jwtService, AuthProperties properties) {
        this.gitHub = gitHub;
        this.usersRepository = usersRepository;
        this.tokenCipher = tokenCipher;
        this.jwtService = jwtService;
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

        IssuedToken token = jwtService.issue(account.id(), account.login());
        return new LoginResult(account.login(), token);
    }

    public record LoginResult(String login, IssuedToken token) {
    }
}

package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.hero.RestDays;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.PreparedResync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The user's settings on {@code users}. The plain saves are short transactions in {@link PreferencesWriter}. A change
 * of the private-repos switch that is <b>effective</b> (B11: the switch flips while GitHub grants {@code repo}) first
 * resyncs the user's data inside the request, with the slow GitHub and LLM calls outside any transaction, and only
 * then commits the switch, the lock and the new insights together.
 */
@Slf4j
@Service
public class PreferencesService {

    /** A lost compare-and-set re-reads and decides again. */
    private static final int ATTEMPTS = 3;

    private final UsersRepository usersRepository;
    private final PreferencesWriter writer;
    private final SyncService syncService;
    private final PrivateProperties properties;
    private final Clock clock;

    public PreferencesService(UsersRepository usersRepository, PreferencesWriter writer, SyncService syncService,
                              PrivateProperties properties, Clock clock) {
        this.usersRepository = usersRepository;
        this.writer = writer;
        this.syncService = syncService;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<Preferences> get(long userId) {
        return usersRepository.findById(userId).map(user -> of(user, clock.instant(), properties.changeCooldown()));
    }

    /**
     * Saves validated preferences, in the order of the contract (B11): the same switch value is a plain save even
     * while locked; a change within the lock refuses the whole PUT (429 "Private access locked"); a change that is
     * not effective is a plain save; an effective change resyncs first.
     *
     * @return empty if the user is gone (404)
     * @throws TooManyRequestsException 429: the lock, or the global sync cap being busy
     * @throws ResponseStatusException  409: GitHub rejects the stored token; nothing was changed
     * @throws com.tushar27x.insightshub.github.GitHubApiException 502: GitHub failed during the resync; nothing was changed
     */
    public Optional<Preferences> update(long userId, Preferences preferences) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Optional<Users> found = usersRepository.findById(userId);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            Users user = found.get();
            Instant now = clock.instant();
            switch (PrivateSwitch.decide(user.isIncludePrivate(), preferences.includePrivate(), user.hasRepoScope(),
                    user.getPrivateChangedAt(), now, properties.changeCooldown())) {
                case PrivateSwitch.Same same -> {
                    return writer.save(userId, preferences);
                }
                case PrivateSwitch.Store store -> {
                    return writer.save(userId, preferences);
                }
                case PrivateSwitch.Locked locked -> throw locked(locked, now);
                case PrivateSwitch.EffectiveChange change -> {
                    Optional<Preferences> saved = effectiveChange(user, preferences, now);
                    if (saved.isPresent()) {
                        return saved;
                    }
                    log.info("Private switch of '{}' moved during the resync; deciding again", user.getLogin());
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "The private repos switch changed while saving; try again");
    }

    private Optional<Preferences> effectiveChange(Users user, Preferences preferences, Instant now) {
        long userId = user.getGithubId();
        boolean expectedInclude = user.isIncludePrivate(); // what the decision was made on, for the compare-and-set
        Instant expectedChangedAt = user.getPrivateChangedAt();
        syncService.acquireGlobalSlot().ifPresent(retryAfter -> {
            throw new TooManyRequestsException("Too many syncs right now; try again shortly", retryAfter);
        });
        PreparedResync resync = syncService.prepareResync(userId, preferences.includePrivate())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "GitHub no longer accepts your token; log in again at /api/auth/login"));
        // the lock starts only if the change really took effect: GitHub may have shown the token lost repo meanwhile
        boolean lock = resync.includePrivate() == preferences.includePrivate();
        Optional<Preferences> saved = writer.commitSwitch(userId, preferences, expectedInclude,
                expectedChangedAt, lock, resync, now);
        saved.ifPresent(p -> syncService.afterResync(userId));
        return saved;
    }

    private static TooManyRequestsException locked(PrivateSwitch.Locked locked, Instant now) {
        return new TooManyRequestsException("Private access locked",
                "Private repo access was changed recently; it can change again at " + locked.lockedUntil(),
                locked.retryAfter(now));
    }

    static Preferences of(Users user, Instant now, Duration cooldown) {
        return new Preferences(user.getTimezone(), RestDays.toDays(user.getRestDays()), user.isAlertsEnabled(),
                user.isPublicProfile(), user.isIncludePrivate(),
                user.hasRepoScope() ? Preferences.PrivateAccess.GRANTED : Preferences.PrivateAccess.NONE,
                PrivateSwitch.lockedUntil(user.getPrivateChangedAt(), now, cooldown));
    }
}

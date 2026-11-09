package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.ResyncWriter;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.PreparedResync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B11: {@code PUT /preferences} and the private switch: the order of checks, the lock, and what an effective change commits. */
class PreferencesSwitchTest {

    private static final long ID = 583231L;
    private static final Instant NOW = Instant.parse("2026-10-16T09:30:00Z");
    private static final Instant LOCK_ENDS = Instant.parse("2026-10-23T09:30:00Z");
    private static final String WITH_REPO = "read:user,user:email,repo";

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final SyncService syncService = mock(SyncService.class);
    private final ResyncWriter resyncWriter = mock(ResyncWriter.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PrivateProperties privateProperties = new PrivateProperties(Duration.ofDays(7));
    private final PreferencesService service = new PreferencesService(usersRepository,
            new PreferencesWriter(usersRepository, resyncWriter, clock, privateProperties), syncService,
            privateProperties, clock);
    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users(ID, "octocat");
        user.setGithubScopes(WITH_REPO);
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        when(syncService.acquireGlobalSlot()).thenReturn(Optional.empty());
        // the compare-and-set as the database does it: the value flips when it still equals the one read
        when(usersRepository.changeIncludePrivate(eq(ID), anyBoolean(), anyBoolean(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    if (user.isIncludePrivate() != call.<Boolean>getArgument(1)) {
                        return 0;
                    }
                    user.setIncludePrivate(call.getArgument(2));
                    return 1;
                });
    }

    private static Preferences body(String zone, boolean includePrivate) {
        return new Preferences(zone, List.of(), true, false, includePrivate, null);
    }

    private PreparedResync resync(boolean includePrivate) {
        return new PreparedResync(ID, includePrivate, false, List.of(), Map.of(), Map.of(),
                AiTestData.insights(), null, NOW);
    }

    private static TooManyRequestsException tooMany(Throwable thrown) {
        assertThat(thrown).isInstanceOf(TooManyRequestsException.class);
        return (TooManyRequestsException) thrown;
    }

    // --- the lock ---

    @Test
    void aFlipWhileLockedIs429AndTheWholePutIsRefused() {
        user.setIncludePrivate(true);
        user.setPrivateChangedAt(NOW.minus(Duration.ofDays(1)));

        assertThatThrownBy(() -> service.update(ID, body("Asia/Kolkata", false))).satisfies(thrown -> {
            TooManyRequestsException e = tooMany(thrown);
            assertThat(e.getBody().getTitle()).isEqualTo("Private access locked");
            assertThat(e.getBody().getDetail()).contains("2026-10-22T09:30:00Z");
            assertThat(e.retryAfterSeconds()).isEqualTo(Duration.ofDays(6).toSeconds());
        });

        assertThat(user.isIncludePrivate()).isTrue();
        assertThat(user.getTimezone()).isEqualTo("UTC"); // no other field was saved
        verify(syncService, never()).prepareResync(anyLong(), anyBoolean());
        verify(resyncWriter, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theSameValueWhileLockedIsA200AndSavesTheOtherFields() {
        user.setIncludePrivate(true);
        user.setPrivateChangedAt(NOW.minus(Duration.ofDays(1)));

        Preferences saved = service.update(ID, body("Asia/Kolkata", true)).orElseThrow();

        assertThat(saved.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(saved.includePrivate()).isTrue();
        assertThat(saved.privateLockedUntil()).isEqualTo(Instant.parse("2026-10-22T09:30:00Z")); // unchanged
        verify(syncService, never()).prepareResync(anyLong(), anyBoolean());
        assertThat(user.getPrivateChangedAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void turningItOffWhileWaitingIsAllowedEvenWhenLocked() {
        user.setGithubScopes("read:user,user:email"); // GitHub shares nothing (private_access none)
        user.setIncludePrivate(true);
        user.setPrivateChangedAt(NOW.minus(Duration.ofDays(1)));

        Preferences saved = service.update(ID, body("UTC", false)).orElseThrow();

        assertThat(saved.includePrivate()).isFalse();
        assertThat(saved.privateAccess()).isEqualTo(Preferences.PrivateAccess.NONE);
        verify(syncService, never()).prepareResync(anyLong(), anyBoolean());
        assertThat(user.getPrivateChangedAt()).isEqualTo(NOW.minus(Duration.ofDays(1))); // untouched
    }

    // --- not effective ---

    @Test
    void aFlipWhileGitHubGrantsNothingOnlyStoresTheWishWithoutAResyncOrALock() {
        user.setGithubScopes("read:user,user:email");

        Preferences saved = service.update(ID, body("UTC", true)).orElseThrow();

        assertThat(saved.includePrivate()).isTrue();
        assertThat(saved.privateAccess()).isEqualTo(Preferences.PrivateAccess.NONE);
        assertThat(saved.privateLockedUntil()).isNull();
        assertThat(user.getPrivateChangedAt()).isNull();
        verify(syncService, never()).acquireGlobalSlot();
        verify(syncService, never()).prepareResync(anyLong(), anyBoolean());
        verify(resyncWriter, never()).write(org.mockito.ArgumentMatchers.any());
    }

    // --- effective ---

    @Test
    void anEffectiveFlipResyncsInsideTheRequestThenCommitsTheSwitchTheFieldsTheLockAndTheInsights() {
        PreparedResync resync = resync(true);
        when(syncService.prepareResync(ID, true)).thenReturn(Optional.of(resync));

        Preferences saved = service.update(ID, body("Asia/Kolkata", true)).orElseThrow();

        assertThat(saved.includePrivate()).isTrue();
        assertThat(saved.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(saved.privateLockedUntil()).isEqualTo(LOCK_ENDS);
        assertThat(user.getPrivateChangedAt()).isEqualTo(NOW);
        var order = inOrder(syncService, usersRepository, resyncWriter);
        order.verify(syncService).acquireGlobalSlot();
        order.verify(syncService).prepareResync(ID, true); // slow work first, with the target value
        order.verify(usersRepository).changeIncludePrivate(ID, false, true, null);
        order.verify(resyncWriter).write(resync);
        verify(syncService).afterResync(ID);
    }

    @Test
    void turningItOffIsAnEffectiveChangeToo() {
        user.setIncludePrivate(true);
        PreparedResync resync = resync(false);
        when(syncService.prepareResync(ID, false)).thenReturn(Optional.of(resync));

        Preferences saved = service.update(ID, body("UTC", false)).orElseThrow();

        assertThat(saved.includePrivate()).isFalse();
        assertThat(saved.privateLockedUntil()).isEqualTo(LOCK_ENDS);
        verify(resyncWriter).write(resync);
    }

    @Test
    void anExpiredLockDoesNotBlockTheNextChange() {
        user.setPrivateChangedAt(NOW.minus(Duration.ofDays(8)));
        when(syncService.prepareResync(ID, true)).thenReturn(Optional.of(resync(true)));

        Preferences saved = service.update(ID, body("UTC", true)).orElseThrow();

        assertThat(saved.privateLockedUntil()).isEqualTo(LOCK_ENDS);
        verify(usersRepository).changeIncludePrivate(eq(ID), eq(false), eq(true), eq(NOW.minus(Duration.ofDays(8))));
    }

    @Test
    void ifTheTokenLostRepoDuringTheResyncTheWishIsStoredWithoutALock() {
        // prepareResync fetched public data only (GitHub no longer grants repo): not an effective change after all
        when(syncService.prepareResync(ID, true)).thenReturn(Optional.of(
                new PreparedResync(ID, false, true, List.of(), Map.of(), Map.of(), AiTestData.insights(), null, NOW)));

        Preferences saved = service.update(ID, body("UTC", true)).orElseThrow();

        assertThat(saved.includePrivate()).isTrue();
        assertThat(saved.privateLockedUntil()).isNull();
        assertThat(user.getPrivateChangedAt()).isNull();
        verify(resyncWriter).write(org.mockito.ArgumentMatchers.any()); // the public-only insights are still written
    }

    // --- a resync that fails commits nothing ---

    @Test
    void aGitHubFailureDuringTheResyncChangesNothing() {
        when(syncService.prepareResync(ID, true)).thenThrow(new GitHubApiException("HTTP 502"));

        assertThatThrownBy(() -> service.update(ID, body("Asia/Kolkata", true))).isInstanceOf(GitHubApiException.class);

        assertThat(user.isIncludePrivate()).isFalse();
        assertThat(user.getPrivateChangedAt()).isNull();
        assertThat(user.getTimezone()).isEqualTo("UTC");
        verify(usersRepository, never()).changeIncludePrivate(anyLong(), anyBoolean(), anyBoolean(), org.mockito.ArgumentMatchers.any());
        verify(resyncWriter, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aDeadTokenIs409AndChangesNothing() {
        when(syncService.prepareResync(ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ID, body("UTC", true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(user.isIncludePrivate()).isFalse();
        assertThat(user.getPrivateChangedAt()).isNull();
        verify(resyncWriter, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theGlobalSyncCapBeingBusyIs429WithThePlainTitleAndChangesNothing() {
        when(syncService.acquireGlobalSlot()).thenReturn(Optional.of(Duration.ofSeconds(17)));

        assertThatThrownBy(() -> service.update(ID, body("UTC", true))).satisfies(thrown -> {
            TooManyRequestsException e = tooMany(thrown);
            assertThat(e.getBody().getTitle()).isEqualTo("Too Many Requests");
            assertThat(e.retryAfterSeconds()).isEqualTo(17);
        });

        verify(syncService, never()).prepareResync(anyLong(), anyBoolean());
        assertThat(user.isIncludePrivate()).isFalse();
    }

    // --- races ---

    @Test
    void aLostRaceThatStoredTheSameValueIsANoOp() {
        when(syncService.prepareResync(ID, true)).thenAnswer(call -> {
            user.setIncludePrivate(true); // a concurrent PUT wins while we are fetching
            user.setPrivateChangedAt(NOW);
            return Optional.of(resync(true));
        });

        Preferences saved = service.update(ID, body("UTC", true)).orElseThrow();

        assertThat(saved.includePrivate()).isTrue();
        verify(resyncWriter, never()).write(org.mockito.ArgumentMatchers.any()); // ours was not committed
        assertThat(saved.privateLockedUntil()).isEqualTo(LOCK_ENDS); // the winner's lock
    }

    @Test
    void aLostRaceThatStoredTheOppositeValueIsTheLock429() {
        when(syncService.prepareResync(ID, true)).thenAnswer(call -> {
            user.setPrivateChangedAt(NOW); // a concurrent change started a lock, then flipped back
            return Optional.of(resync(true));
        });
        // the CAS expects privateChangedAt == null, which no longer holds
        org.mockito.Mockito.doReturn(0).when(usersRepository).changeIncludePrivate(ID, false, true, null);

        assertThatThrownBy(() -> service.update(ID, body("UTC", true))).satisfies(thrown ->
                assertThat(tooMany(thrown).getBody().getTitle()).isEqualTo("Private access locked"));
    }

    @Test
    void anUnknownUserIsEmpty() {
        assertThat(service.update(42L, body("UTC", true))).isEmpty();
    }
}

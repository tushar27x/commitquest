package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.user.PrivateSwitch.Decision;
import com.tushar27x.insightshub.user.PrivateSwitch.EffectiveChange;
import com.tushar27x.insightshub.user.PrivateSwitch.Locked;
import com.tushar27x.insightshub.user.PrivateSwitch.Same;
import com.tushar27x.insightshub.user.PrivateSwitch.Store;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code PUT /preferences}' private switch (B11), mirroring {@code ClassSwitchTest}: the order of the checks. */
class PrivateSwitchTest {

    private static final Instant NOW = Instant.parse("2026-10-16T09:30:00Z");
    private static final Duration WEEK = Duration.ofDays(7);
    private static final Instant LOCK_ENDS = Instant.parse("2026-10-23T09:30:00Z");
    /** Changed now: locked until {@link #LOCK_ENDS}. */
    private static final Instant JUST_CHANGED = NOW;
    private static final Instant LONG_AGO = Instant.parse("2026-10-01T00:00:00Z");

    private static Decision decide(boolean stored, boolean requested, boolean granted, Instant changedAt) {
        return PrivateSwitch.decide(stored, requested, granted, changedAt, NOW, WEEK);
    }

    @Test
    void theStoredValueAgainIsANoOpEvenWhileLockedWhateverTheAccess() {
        assertThat(decide(true, true, true, JUST_CHANGED)).isInstanceOf(Same.class);
        assertThat(decide(false, false, true, JUST_CHANGED)).isInstanceOf(Same.class);
        assertThat(decide(true, true, false, JUST_CHANGED)).isInstanceOf(Same.class);
        assertThat(decide(false, false, false, null)).isInstanceOf(Same.class);
    }

    @Test
    void aFlipWhileLockedIsRefusedInBothDirectionsWhenAccessIsGranted() {
        assertThat(decide(false, true, true, JUST_CHANGED)).isEqualTo(new Locked(LOCK_ENDS));
        assertThat(decide(true, false, true, JUST_CHANGED)).isEqualTo(new Locked(LOCK_ENDS));
    }

    @Test
    void turningItOnWhileLockedIsRefusedEvenWithoutAccess() {
        assertThat(decide(false, true, false, JUST_CHANGED)).isEqualTo(new Locked(LOCK_ENDS));
    }

    @Test
    void theOneWayOutOfTheLockIsOffWhileWaitingForGitHub() {
        // true + none: nothing effective is running, so turning it off changes nothing effective
        assertThat(decide(true, false, false, JUST_CHANGED)).isInstanceOf(Store.class);
    }

    @Test
    void theLockEndsExactlyAtTheCooldown() {
        assertThat(decide(false, true, true, NOW.minus(WEEK))).isInstanceOf(EffectiveChange.class); // ends now: free
        assertThat(decide(false, true, true, NOW.minus(WEEK).plusSeconds(1))).isInstanceOf(Locked.class);
        assertThat(decide(false, true, true, LONG_AGO)).isInstanceOf(EffectiveChange.class);
    }

    @Test
    void theFirstChangeEverIsAlwaysAllowed() {
        assertThat(decide(false, true, true, null)).isInstanceOf(EffectiveChange.class);
        assertThat(decide(true, false, true, null)).isInstanceOf(EffectiveChange.class);
    }

    @Test
    void aFlipWhileGitHubGrantsNothingOnlyStoresTheWish() {
        assertThat(decide(false, true, false, null)).isInstanceOf(Store.class);
        assertThat(decide(false, true, false, LONG_AGO)).isInstanceOf(Store.class);
        assertThat(decide(true, false, false, LONG_AGO)).isInstanceOf(Store.class);
    }

    @Test
    void lockedUntilIsNullWhenNeverChangedOrPassed() {
        assertThat(PrivateSwitch.lockedUntil(null, NOW, WEEK)).isNull();
        assertThat(PrivateSwitch.lockedUntil(LONG_AGO, NOW, WEEK)).isNull();
        assertThat(PrivateSwitch.lockedUntil(JUST_CHANGED, NOW, WEEK)).isEqualTo(LOCK_ENDS);
    }

    @Test
    void retryAfterIsTheTimeLeft() {
        assertThat(new Locked(LOCK_ENDS).retryAfter(NOW)).isEqualTo(WEEK);
        assertThat(new Locked(LOCK_ENDS).retryAfter(NOW.plus(Duration.ofDays(2)))).isEqualTo(Duration.ofDays(5));
    }
}

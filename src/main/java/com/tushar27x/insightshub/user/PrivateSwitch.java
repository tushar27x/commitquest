package com.tushar27x.insightshub.user;

import java.time.Duration;
import java.time.Instant;

/**
 * {@code PUT /preferences}' private-repos switch (B11) as one pure function, like
 * {@link com.tushar27x.insightshub.hero.ClassSwitch}: no clock, no database. Checked in this order:
 * <ol>
 *   <li>the stored value again: {@link Same}, even while locked;</li>
 *   <li>within the lock: {@link Locked}, except {@code true -> false} while GitHub shares no private scope, which
 *       changes nothing effective;</li>
 *   <li>not effective-changing (the scope is not granted, so the switch is only a wish): {@link Store};</li>
 *   <li>otherwise an {@link EffectiveChange}: a full resync, then the lock.</li>
 * </ol>
 */
public final class PrivateSwitch {

    private PrivateSwitch() {
    }

    /**
     * @param stored        {@code users.include_private}
     * @param requested     the value in the PUT
     * @param accessGranted whether the stored token has {@code repo} ({@code private_access = granted})
     * @param changedAt     {@code users.private_changed_at}; null if never changed
     */
    public static Decision decide(boolean stored, boolean requested, boolean accessGranted, Instant changedAt,
                                  Instant now, Duration cooldown) {
        if (requested == stored) {
            return new Same();
        }
        Instant until = lockedUntil(changedAt, now, cooldown);
        if (until != null && (accessGranted || requested)) { // the one way out of the lock: off while waiting
            return new Locked(until);
        }
        return accessGranted ? new EffectiveChange() : new Store();
    }

    /** The end of the lock, or null if never changed or the lock has passed. */
    public static Instant lockedUntil(Instant changedAt, Instant now, Duration cooldown) {
        if (changedAt == null) {
            return null;
        }
        Instant until = changedAt.plus(cooldown);
        return until.isAfter(now) ? until : null;
    }

    public sealed interface Decision permits Same, Locked, Store, EffectiveChange {
    }

    /** The request equals the stored value: the normal save, no resync, no lock. */
    public record Same() implements Decision {
    }

    /** -> 429 "Private access locked" with {@code Retry-After}; the whole PUT is refused. */
    public record Locked(Instant lockedUntil) implements Decision {

        public Duration retryAfter(Instant now) {
            return Duration.between(now, lockedUntil);
        }
    }

    /** Only the wish changes (waiting for GitHub): the normal save, no resync, no lock. */
    public record Store() implements Decision {
    }

    /** include_private flips while the scope is granted: resync inside the request, then lock. */
    public record EffectiveChange() implements Decision {
    }
}

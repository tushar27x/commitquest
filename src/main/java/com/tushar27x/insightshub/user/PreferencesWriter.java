package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.hero.RestDays;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.ResyncWriter;
import com.tushar27x.insightshub.sync.SyncService.PreparedResync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The short transactions of {@code PUT /preferences}. A separate bean from {@link PreferencesService} because
 * {@code @Transactional} only applies through the proxy, and the service must not hold a transaction across the slow
 * resync of an effective private-access change.
 */
@Slf4j
@Component
public class PreferencesWriter {

    private final UsersRepository usersRepository;
    private final ResyncWriter resyncWriter;
    private final Clock clock;
    private final PrivateProperties properties;

    public PreferencesWriter(UsersRepository usersRepository, ResyncWriter resyncWriter, Clock clock,
                             PrivateProperties properties) {
        this.properties = properties;
        this.usersRepository = usersRepository;
        this.resyncWriter = resyncWriter;
        this.clock = clock;
    }

    /**
     * Saves validated preferences. A rest-day change applies from tomorrow in the (new) zone: today keeps the
     * mask that applied on it, stored as {@code rest_days_previous}, so the hero job settles today as it was.
     * {@code include_private} is stored as given: the caller has decided it is no effective change (or no change).
     */
    @Transactional
    public Optional<Preferences> save(long userId, Preferences preferences) {
        return usersRepository.findById(userId).map(user -> {
            apply(user, preferences);
            user.setIncludePrivate(preferences.includePrivate());
            return PreferencesService.of(user, clock.instant(), properties.changeCooldown()); // managed entity: flushed on commit
        });
    }

    /**
     * B11, an effective change of private access: the new switch value, the other fields, the lock and the prepared
     * resync's writes commit together. Anything thrown rolls all of it back.
     *
     * @param expectedInclude   the switch value the decision was made on
     * @param expectedChangedAt {@code private_changed_at} as the decision read it
     * @param lock              whether this starts the lock (false when GitHub turned out not to grant {@code repo})
     * @return empty if the switch moved since the decision was made (nothing was written), or the user is gone
     */
    @Transactional
    public Optional<Preferences> commitSwitch(long userId, Preferences preferences, boolean expectedInclude,
                                              Instant expectedChangedAt, boolean lock, PreparedResync resync,
                                              Instant now) {
        if (usersRepository.changeIncludePrivate(userId, expectedInclude, preferences.includePrivate(),
                expectedChangedAt) == 0) {
            return Optional.empty();
        }
        Optional<Users> found = usersRepository.findById(userId); // after the compare-and-set: sees the new value
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Users user = found.get();
        apply(user, preferences);
        if (lock) {
            user.setPrivateChangedAt(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)); // whole seconds: the API shows it
        }
        resyncWriter.write(resync);
        log.info("Private repos for '{}' turned {} ({}): resynced{}", user.getLogin(),
                preferences.includePrivate() ? "on" : "off", lock ? "locked" : "not locked, GitHub grants no repo scope",
                resync.scopeLost() ? ", scope lost" : "");
        return Optional.of(PreferencesService.of(user, clock.instant(), properties.changeCooldown()));
    }

    /** Everything but the private switch. */
    private void apply(Users user, Preferences preferences) {
        int mask = RestDays.toMask(preferences.restDays());
        if (mask != user.getRestDays()) {
            LocalDate today = LocalDate.ofInstant(clock.instant(), HeroService.zoneOf(preferences.timezone()));
            int applyingToday = RestDays.maskOn(today, user.getRestDays(), user.getRestDaysPrevious(),
                    user.getRestDaysFrom());
            user.setRestDaysPrevious((short) applyingToday);
            user.setRestDays((short) mask);
            user.setRestDaysFrom(today.plusDays(1));
        }
        user.setTimezone(preferences.timezone());
        user.setAlertsEnabled(preferences.alertsEnabled());
        user.setPublicProfile(preferences.publicProfile());
        log.info("Preferences for '{}' saved: zone {}, rest days {}, alerts {}, public {}, private repos {}",
                user.getLogin(), preferences.timezone(), preferences.restDays(), preferences.alertsEnabled(),
                preferences.publicProfile(), preferences.includePrivate());
    }
}

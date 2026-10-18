package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.hero.RestDays;
import com.tushar27x.insightshub.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/** The user's settings on {@code users}. */
@Slf4j
@Service
public class PreferencesService {

    private final UsersRepository usersRepository;
    private final Clock clock;

    public PreferencesService(UsersRepository usersRepository, Clock clock) {
        this.usersRepository = usersRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<Preferences> get(long userId) {
        return usersRepository.findById(userId).map(PreferencesService::of);
    }

    /**
     * Saves validated preferences. A rest-day change applies from tomorrow in the (new) zone: today keeps the
     * mask that applied on it, stored as {@code rest_days_previous}, so the hero job settles today as it was.
     */
    @Transactional
    public Optional<Preferences> update(long userId, Preferences preferences) {
        return usersRepository.findById(userId).map(user -> {
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
            log.info("Preferences for '{}' saved: zone {}, rest days {}, alerts {}, public {}", user.getLogin(),
                    preferences.timezone(), preferences.restDays(), preferences.alertsEnabled(),
                    preferences.publicProfile());
            return of(user); // managed entity: flushed on commit
        });
    }

    static Preferences of(Users user) {
        return new Preferences(user.getTimezone(), RestDays.toDays(user.getRestDays()), user.isAlertsEnabled(),
                user.isPublicProfile());
    }
}

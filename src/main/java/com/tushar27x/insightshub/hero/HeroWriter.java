package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.hero.HeroRules.CatchUp;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.repository.HeroRepository.SettledDay;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The hero's writes, each in one transaction. A separate bean like {@code sync.InsightsWriter}:
 * {@code @Transactional} only applies through the Spring proxy, not on a call from inside {@link HeroService}.
 */
@Component
public class HeroWriter {

    private final HeroRepository heroes;
    private final UsersRepository usersRepository;

    public HeroWriter(HeroRepository heroes, UsersRepository usersRepository) {
        this.heroes = heroes;
        this.usersRepository = usersRepository;
    }

    /** @return false if the user already had a hero */
    @Transactional
    public boolean seed(long userId, HeroState state, Instant now) {
        return heroes.insertState(userId, state, now);
    }

    /**
     * Saves a catch-up: the new state, its events and one {@code hero_days} row per settled day, all or nothing.
     * The state moves only if it is still at {@code expectedLastEvaluatedOn}, so when two runs settle the same
     * days (two instances, or a retry), the second writes nothing. The unique keys on the events and days are a
     * second guard.
     *
     * @return false if nothing was settled or another run already moved the hero
     */
    @Transactional
    public boolean apply(long userId, LocalDate expectedLastEvaluatedOn, CatchUp catchUp, Instant now) {
        if (catchUp.days().isEmpty()
                || !heroes.updateState(userId, catchUp.state(), expectedLastEvaluatedOn, now)) {
            return false;
        }
        heroes.insertEvents(userId, catchUp.events());
        heroes.insertDays(userId, catchUp.days().stream()
                .map(day -> new SettledDay(day.state().lastEvaluatedOn(), day.xpGained(), day.state().streak()))
                .toList());
        return true;
    }

    /**
     * Saves a class switch and its {@code CLASS_CHANGED} event, all or nothing. The class moves only if
     * {@code archetype_changed_at} is still {@code expectedChangedAt}, so of two racing switches one wins and
     * the other writes nothing. The event's unique key (user, type, day) is safe because of the cooldown.
     *
     * @return false if the class was changed since {@code expectedChangedAt} was read
     */
    @Transactional
    public boolean changeClass(long userId, Instant expectedChangedAt, ClassSwitch.Change change, Instant now) {
        if (usersRepository.changeClass(userId, change.chosen(), now, expectedChangedAt) != 1) {
            return false;
        }
        heroes.insertEvents(userId, List.of(change.event()));
        return true;
    }
}

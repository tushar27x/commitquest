package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.hero.HeroRules.CatchUp;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.repository.HeroRepository.SettledDay;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The hero's writes, each in one transaction. A separate bean like {@code sync.InsightsWriter}:
 * {@code @Transactional} only applies through the Spring proxy, not on a call from inside {@link HeroService}.
 */
@Component
public class HeroWriter {

    private final HeroRepository heroes;

    public HeroWriter(HeroRepository heroes) {
        this.heroes = heroes;
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
}

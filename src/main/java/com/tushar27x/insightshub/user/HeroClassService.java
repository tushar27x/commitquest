package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.hero.ClassSwitch;
import com.tushar27x.insightshub.hero.ClassSwitch.Change;
import com.tushar27x.insightshub.hero.ClassSwitch.CoolingDown;
import com.tushar27x.insightshub.hero.ClassSwitch.NotQualified;
import com.tushar27x.insightshub.hero.ClassSwitch.Unchanged;
import com.tushar27x.insightshub.hero.HeroProperties;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.hero.HeroWriter;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * {@code PUT /api/user/hero/class}: the rules are {@link ClassSwitch}, the write is one transaction in
 * {@link HeroWriter#changeClass}. Reads happen outside it (open-in-view is off, so every read is fresh).
 */
@Slf4j
@Service
public class HeroClassService {

    private final UsersRepository usersRepository;
    private final InsightsRepository insightsRepository;
    private final HeroRepository heroes;
    private final HeroWriter writer;
    private final HeroQueryService heroQueryService;
    private final HeroProperties properties;
    private final Clock clock;

    public HeroClassService(UsersRepository usersRepository, InsightsRepository insightsRepository,
                            HeroRepository heroes, HeroWriter writer, HeroQueryService heroQueryService,
                            HeroProperties properties, Clock clock) {
        this.usersRepository = usersRepository;
        this.insightsRepository = insightsRepository;
        this.heroes = heroes;
        this.writer = writer;
        this.heroQueryService = heroQueryService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Switches the hero's class, or goes back to following the top score ({@code requested == null}).
     *
     * @return the updated hero; empty if the user has no hero yet (404)
     * @throws ResponseStatusException 409 if the class isn't qualified
     * @throws TooManyRequestsException 429 within the cooldown of the last change
     */
    public Optional<HeroResponse> change(long userId, Archetype requested) {
        Optional<Insights> found = insightsRepository.findById(userId); // with its user
        if (found.isEmpty() || heroes.findState(userId).isEmpty()) {
            return Optional.empty();
        }
        Insights insights = found.get();
        Users user = insights.getUser();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, HeroService.zoneOf(user.getTimezone()));

        switch (ClassSwitch.decide(HeroQueryService.scores(insights), insights.getArchetype(),
                user.getChosenArchetype(), user.getArchetypeChangedAt(), requested, now, today,
                properties.qualifyScore(), properties.classCooldown())) {
            case Unchanged unchanged -> {
            }
            case NotQualified notQualified -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "%s isn't qualified: it scores %d, and a class needs %d or the top score".formatted(
                            notQualified.requested(), notQualified.score(), properties.qualifyScore()));
            case CoolingDown cooling -> throw coolingDown(cooling, now);
            case Change change -> {
                if (writer.changeClass(userId, user.getArchetypeChangedAt(), change, now)) {
                    log.info("Class for '{}' changed: {}", user.getLogin(), change.event().details());
                } else {
                    lostTheRace(userId, requested, now);
                }
            }
        }
        return heroQueryService.hero(userId);
    }

    /**
     * Another switch was stored between our read and our write. If it stored what we asked for, ours is a
     * no-op; otherwise its change started a new cooldown, read back fresh for {@code Retry-After}.
     */
    private void lostTheRace(long userId, Archetype requested, Instant now) {
        Users fresh = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        if (fresh.getChosenArchetype() == requested) {
            return;
        }
        Instant changedAt = fresh.getArchetypeChangedAt() == null ? now : fresh.getArchetypeChangedAt();
        throw coolingDown(new CoolingDown(changedAt.plus(properties.classCooldown())), now);
    }

    private static TooManyRequestsException coolingDown(CoolingDown cooling, Instant now) {
        return new TooManyRequestsException("The class was changed recently; it can change again at "
                + cooling.canSwitchAt(), cooling.retryAfter(now));
    }
}

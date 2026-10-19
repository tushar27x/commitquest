package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroProperties;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.hero.HeroWriter;
import com.tushar27x.insightshub.hero.InMemoryHeroRepository;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.tushar27x.insightshub.entity.Archetype.BARD;
import static com.tushar27x.insightshub.entity.Archetype.MONK;
import static com.tushar27x.insightshub.entity.Archetype.PALADIN;
import static com.tushar27x.insightshub.entity.Archetype.ROGUE;
import static com.tushar27x.insightshub.entity.Archetype.WIZARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code PUT /hero/class} through the real rules, writer and read side; the repositories are in memory or
 * mocked, with {@code changeClass} acting as the compare-and-set it is in SQL (the SQL itself: {@code it/}).
 */
class HeroClassServiceTest {

    private static final long ID = 583231L;
    /** Friday 16 Oct 2026, 18:20 in Kolkata. */
    private static final Instant NOW = Instant.parse("2026-10-16T12:50:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final HeroState STATE = new HeroState(7, 140, 2, 1, TODAY.minusDays(1));

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final InsightsRepository insightsRepository = mock(InsightsRepository.class);
    private final DailyContributionsRepository activity = mock(DailyContributionsRepository.class);
    private final InMemoryHeroRepository heroes = new InMemoryHeroRepository();
    private final HeroProperties properties = new HeroProperties(4, 40, Duration.ofDays(7));
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final HeroClassService service = new HeroClassService(usersRepository, insightsRepository, heroes,
            new HeroWriter(heroes, usersRepository),
            new HeroQueryService(usersRepository, insightsRepository, heroes, activity, properties, clock),
            properties, clock);
    private Users user;
    /** Set to make the next compare-and-set lose to another request that stored this. */
    private Runnable racingWinner;

    @BeforeEach
    void setUp() {
        user = new Users(ID, "octocat");
        user.setTimezone("Asia/Kolkata");
        Insights insights = new Insights(user);
        insights.setArchetype(MONK);
        insights.setArchetypeScores(new HashMap<>(Map.of("PALADIN", 44, "BARBARIAN", 44, "WIZARD", 33,
                "ARTIFICER", 33, "CLERIC", 0, "BARD", 10, "MONK", 52, "ROGUE", 47)));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(insights));
        when(activity.findCounts(anyLong(), any(), any())).thenReturn(Map.of());
        when(usersRepository.changeClass(eq(ID), any(), any(), any())).thenAnswer(invocation -> {
            if (racingWinner != null) {
                racingWinner.run();
            }
            if (!Objects.equals(user.getArchetypeChangedAt(), invocation.getArgument(3))) {
                return 0;
            }
            user.setChosenArchetype(invocation.getArgument(1));
            user.setArchetypeChangedAt(invocation.getArgument(2));
            return 1;
        });
        heroes.states.put(ID, STATE);
    }

    @Test
    void aQualifiedClassIsStoredAndReturnedInTheHero() {
        HeroResponse hero = service.change(ID, ROGUE).orElseThrow();

        assertThat(user.getChosenArchetype()).isEqualTo(ROGUE);
        assertThat(user.getArchetypeChangedAt()).isEqualTo(NOW);
        assertThat(hero.heroClass().chosen()).isEqualTo(ROGUE);
        assertThat(hero.heroClass().current()).isEqualTo(ROGUE);
        assertThat(hero.heroClass().scored()).isEqualTo(MONK);
        assertThat(hero.heroClass().canSwitchAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(heroes.eventsOf(ID)).containsExactly(event(MONK, ROGUE, ROGUE));
    }

    @Test
    void levelXpLivesAndStreakDoNotChange() {
        HeroResponse hero = service.change(ID, ROGUE).orElseThrow();

        assertThat(heroes.states.get(ID)).isEqualTo(STATE);
        assertThat(hero.level()).isEqualTo(7);
        assertThat(hero.xp()).isEqualTo(140);
        assertThat(hero.lives()).isEqualTo(2);
        assertThat(hero.streak()).isEqualTo(1);
    }

    @Test
    void theEventIsOnTheUsersLocalDay() {
        // 20:00 UTC on Friday is already Saturday 01:30 in Kolkata
        Clock late = Clock.fixed(Instant.parse("2026-10-16T20:00:00Z"), ZoneOffset.UTC);
        HeroClassService lateService = new HeroClassService(usersRepository, insightsRepository, heroes,
                new HeroWriter(heroes, usersRepository),
                new HeroQueryService(usersRepository, insightsRepository, heroes, activity, properties, late),
                properties, late);

        lateService.change(ID, ROGUE);

        assertThat(heroes.eventsOf(ID)).extracting(HeroEvent::onDate).containsExactly(TODAY.plusDays(1));
    }

    @Test
    void notQualifiedIs409AndWritesNothing() {
        assertThatThrownBy(() -> service.change(ID, WIZARD))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("WIZARD isn't qualified: it scores 33, and a class needs 40 or the top score");
                });
        assertThat(user.getChosenArchetype()).isNull();
        assertThat(heroes.eventsOf(ID)).isEmpty();
    }

    @Test
    void withinTheCooldownIs429WithTheTimeLeft() {
        user.setChosenArchetype(ROGUE);
        user.setArchetypeChangedAt(NOW.minus(Duration.ofDays(5)));

        assertThatThrownBy(() -> service.change(ID, PALADIN))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(Duration.ofDays(2).toSeconds()));
        assertThatThrownBy(() -> service.change(ID, null)).isInstanceOf(TooManyRequestsException.class);
        assertThat(user.getChosenArchetype()).isEqualTo(ROGUE);
        assertThat(heroes.eventsOf(ID)).isEmpty();
    }

    @Test
    void theStoredChoiceAgainIs200WithNoEventEvenInTheCooldown() {
        Instant changed = NOW.minus(Duration.ofDays(1));
        user.setChosenArchetype(ROGUE);
        user.setArchetypeChangedAt(changed);

        assertThat(service.change(ID, ROGUE).orElseThrow().heroClass().chosen()).isEqualTo(ROGUE);
        assertThat(user.getArchetypeChangedAt()).isEqualTo(changed);
        assertThat(heroes.eventsOf(ID)).isEmpty();
    }

    @Test
    void backToTheTopScoreStoresNullAndStartsTheCooldown() {
        user.setChosenArchetype(BARD);
        user.setArchetypeChangedAt(NOW.minus(Duration.ofDays(30)));

        HeroResponse hero = service.change(ID, null).orElseThrow();

        assertThat(user.getChosenArchetype()).isNull();
        assertThat(user.getArchetypeChangedAt()).isEqualTo(NOW);
        assertThat(hero.heroClass().chosen()).isNull();
        assertThat(hero.heroClass().current()).isEqualTo(MONK);
        assertThat(heroes.eventsOf(ID)).containsExactly(event(BARD, MONK, null));
    }

    @Test
    void pinningTheTopClassIsAChangeFromItselfToItself() {
        service.change(ID, MONK);

        assertThat(user.getChosenArchetype()).isEqualTo(MONK);
        assertThat(heroes.eventsOf(ID)).containsExactly(event(MONK, MONK, MONK));
    }

    @Test
    void losingARaceToADifferentChangeIs429FromTheWinnersFreshChange() {
        Instant winnerAt = NOW.minusSeconds(1);
        racingWinner = () -> {
            user.setChosenArchetype(PALADIN);
            user.setArchetypeChangedAt(winnerAt);
            racingWinner = null;
        };

        assertThatThrownBy(() -> service.change(ID, ROGUE))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.retryAfterSeconds())
                        .isEqualTo(Duration.ofDays(7).minusSeconds(1).toSeconds()));
        assertThat(user.getChosenArchetype()).isEqualTo(PALADIN);
        assertThat(heroes.eventsOf(ID)).isEmpty(); // ours; the winner wrote its own
    }

    @Test
    void losingARaceToTheSameChangeIsANoOp() {
        racingWinner = () -> {
            user.setChosenArchetype(ROGUE);
            user.setArchetypeChangedAt(NOW.minusSeconds(1));
            racingWinner = null;
        };

        assertThat(service.change(ID, ROGUE).orElseThrow().heroClass().chosen()).isEqualTo(ROGUE);
    }

    @Test
    void noHeroYetIsEmpty() {
        heroes.states.clear();

        assertThat(service.change(ID, ROGUE)).isEmpty();
        assertThat(user.getChosenArchetype()).isNull();
    }

    @Test
    void noInsightsYetIsEmpty() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());

        assertThat(service.change(ID, ROGUE)).isEmpty();
    }

    private static HeroEvent event(Archetype from, Archetype to, Archetype chosen) {
        Map<String, Object> details = new HashMap<>();
        details.put("from", from.name());
        details.put("to", to.name());
        details.put("chosen", chosen == null ? null : chosen.name());
        return new HeroEvent(HeroEventType.CLASS_CHANGED, TODAY, details);
    }
}

package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.RestDays;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Saving preferences, and the rule that a rest-day change applies from the next local day. */
class PreferencesServiceTest {

    private static final long ID = 583231L;
    /** 20:00 UTC on Friday 16 Oct: already Saturday 01:30 in Kolkata. */
    private static final Instant NOW = Instant.parse("2026-10-16T20:00:00Z");

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final PreferencesService service = new PreferencesService(usersRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users(ID, "octocat");
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
    }

    private static int mask(DayOfWeek... days) {
        return RestDays.toMask(Set.of(days));
    }

    @Test
    void aNewUserHasUtcNoRestDaysAndNoAlerts() {
        assertThat(service.get(ID)).contains(new Preferences("UTC", List.of(), false, false));
    }

    @Test
    void savesEverythingAndReturnsWhatWasSaved() {
        Preferences sent = new Preferences("Asia/Kolkata", List.of(SUNDAY), true, true);

        assertThat(service.update(ID, sent)).contains(sent);
        assertThat(user.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(user.isAlertsEnabled()).isTrue();
        assertThat(user.isPublicProfile()).isTrue();
    }

    @Test
    void aRestDayChangeAppliesFromTomorrowInTheNewZone() {
        service.update(ID, new Preferences("Asia/Kolkata", List.of(SUNDAY, SATURDAY), false, false));

        assertThat((int) user.getRestDays()).isEqualTo(mask(SATURDAY, SUNDAY));
        assertThat((int) user.getRestDaysPrevious()).isZero();
        assertThat(user.getRestDaysFrom()).isEqualTo(LocalDate.of(2026, 10, 18)); // it's Saturday 17th there
    }

    @Test
    void aSecondChangeOnTheSameDayKeepsTodaysMask() {
        user.setRestDays((short) mask(SUNDAY));
        user.setRestDaysPrevious((short) mask(FRIDAY));
        user.setRestDaysFrom(LocalDate.of(2026, 10, 17)); // changed earlier today (UTC)

        service.update(ID, new Preferences("UTC", List.of(SATURDAY), false, false));

        assertThat((int) user.getRestDays()).isEqualTo(mask(SATURDAY));
        assertThat((int) user.getRestDaysPrevious()).isEqualTo(mask(FRIDAY)); // what Friday 16th is settled with
        assertThat(user.getRestDaysFrom()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void anOlderChangeBecomesThePreviousMask() {
        user.setRestDays((short) mask(SUNDAY));
        user.setRestDaysPrevious((short) mask(FRIDAY));
        user.setRestDaysFrom(LocalDate.of(2026, 9, 1));

        service.update(ID, new Preferences("UTC", List.of(), false, false));

        assertThat((int) user.getRestDays()).isZero();
        assertThat((int) user.getRestDaysPrevious()).isEqualTo(mask(SUNDAY));
    }

    @Test
    void sameRestDaysInAnotherOrderChangeNothing() {
        user.setRestDays((short) mask(SATURDAY, SUNDAY));
        user.setRestDaysFrom(LocalDate.of(2026, 9, 1));

        service.update(ID, new Preferences("UTC", List.of(SUNDAY, SATURDAY), true, false));

        assertThat(user.getRestDaysFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(service.get(ID).orElseThrow().restDays()).containsExactly(SATURDAY, SUNDAY); // Monday first
    }

    @Test
    void anUnknownUserIsEmpty() {
        assertThat(service.get(42L)).isEmpty();
        assertThat(service.update(42L, new Preferences("UTC", List.of(), false, false))).isEmpty();
    }
}

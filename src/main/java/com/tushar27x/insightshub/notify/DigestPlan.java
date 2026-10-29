package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 12's rules as pure functions, like {@code HeroRules}: when a user is due a digest, and which of their
 * unsent {@code hero_events} it mails or skips. No clock, DB or Spring.
 * <ul>
 *   <li><b>Notable</b> events make an email worth sending (plan §12): {@code LIFE_LOST} at 1 heart left,
 *       {@code LEVEL_DOWN}, {@code FAINTED}, {@code LEVEL_UP}. A {@code LIFE_LOST} at 0 hearts always comes with
 *       a {@code LEVEL_DOWN} or {@code FAINTED}, which carries the news.</li>
 *   <li>With a fresh notable event, the email covers <b>every</b> fresh event (the others as short "also" lines).
 *       Without one, every event is skipped: stamped with a {@code SKIPPED} digest, so they don't pile up and
 *       a user who turns alerts on later doesn't get a flood.</li>
 *   <li>Events more than {@value #MAX_AGE_DAYS} days before the local today are stale, skipped and never mailed
 *       (a long catch-up, a user who was in {@code needs_reauth}).</li>
 *   <li>Due: once a local day, from {@code sendAt} local time, the morning after the hero job settled
 *       yesterday (06:00 local at the latest).</li>
 * </ul>
 */
public final class DigestPlan {

    static final int MAX_AGE_DAYS = 7;

    private DigestPlan() {
    }

    public static boolean isNotable(HeroEvent event) {
        return switch (event.type()) {
            case LEVEL_UP, LEVEL_DOWN, FAINTED -> true;
            case LIFE_LOST -> event.details().get("lives") instanceof Number lives && lives.intValue() == 1;
            case LIFE_GAINED, RESTED, CLASS_CHANGED -> false;
        };
    }

    /**
     * @param unsent   the user's events with {@code digest_id IS NULL}, oldest first
     * @param today    the user's local today
     * @param eligible alerts on, an email address, and a token GitHub accepts
     */
    public static Plan plan(List<UnsentEvent> unsent, LocalDate today, boolean eligible) {
        LocalDate oldestFresh = today.minusDays(MAX_AGE_DAYS);
        List<UnsentEvent> fresh = new ArrayList<>();
        List<UnsentEvent> stale = new ArrayList<>();
        for (UnsentEvent event : unsent) {
            (event.event().onDate().isBefore(oldestFresh) ? stale : fresh).add(event);
        }
        if (eligible && fresh.stream().anyMatch(event -> isNotable(event.event()))) {
            return new Plan(fresh, stale);
        }
        return new Plan(List.of(), List.copyOf(unsent));
    }

    /**
     * @param lastSentAt the user's last {@code SENT} digest, or null
     * @return whether {@code now} is at or after {@code sendAt} on the user's local day, and no digest went out
     * earlier that local day
     */
    public static boolean isDue(Instant now, ZoneId zone, LocalTime sendAt, Instant lastSentAt) {
        ZonedDateTime local = now.atZone(zone);
        if (local.toLocalTime().isBefore(sendAt)) {
            return false;
        }
        return lastSentAt == null || LocalDate.ofInstant(lastSentAt, zone).isBefore(local.toLocalDate());
    }

    public static LocalDate localToday(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone);
    }

    /** A row of {@code hero_events} with its id, so exactly what was mailed gets stamped. */
    public record UnsentEvent(long id, HeroEvent event) {
    }

    /**
     * @param mail the events the email covers, oldest first (empty: no email)
     * @param skip the events stamped {@code SKIPPED}
     */
    public record Plan(List<UnsentEvent> mail, List<UnsentEvent> skip) {

        public Plan {
            mail = List.copyOf(mail);
            skip = List.copyOf(skip);
        }

        public boolean sends() {
            return !mail.isEmpty();
        }
    }
}

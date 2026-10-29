package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * What a digest says, decided by code (the LLM only writes the intro): one line per event, the notable ones as
 * headlines, a subject from the latest headline, and a static intro for when the AI is down. Pure.
 *
 * @param headlines the notable events ({@link DigestPlan#isNotable}), oldest first
 * @param also      the other events, oldest first
 */
public record DigestContent(List<Line> headlines, List<Line> also, String subject, String fallbackIntro) {

    static final String INTRO_GOOD = "Your hero leveled up. Steady work adds up, so keep the rhythm that got you here.";
    static final String INTRO_SETBACK = "A few quiet days caught up with your hero. That happens to everyone, and "
            + "breaks are part of the job: one commit today starts a new streak.";
    static final String INTRO_MIXED = "A mixed few days for your hero, with progress and some quiet days. "
            + "Here's the story so far; one commit today keeps it moving.";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    public static DigestContent of(List<HeroEvent> events) {
        List<HeroEvent> notable = events.stream().filter(DigestPlan::isNotable).toList();
        if (notable.isEmpty()) {
            throw new IllegalArgumentException("A digest needs at least one notable event");
        }
        List<Line> headlines = notable.stream().map(Line::of).toList();
        List<Line> also = events.stream().filter(event -> !DigestPlan.isNotable(event)).map(Line::of).toList();
        boolean up = notable.stream().anyMatch(event -> event.type() == HeroEventType.LEVEL_UP);
        boolean down = notable.stream().anyMatch(event -> event.type() != HeroEventType.LEVEL_UP);
        String intro = up && down ? INTRO_MIXED : up ? INTRO_GOOD : INTRO_SETBACK;
        return new DigestContent(headlines, also, subject(notable.getLast()), intro);
    }

    static String describe(HeroEvent event) {
        return switch (event.type()) {
            case LEVEL_UP -> "Your hero reached level " + number(event, "to") + ".";
            case LEVEL_DOWN -> "Your hero ran out of hearts and stepped back to level " + number(event, "to")
                    + ", with 3 fresh hearts.";
            case FAINTED -> "Your hero ran out of hearts and fainted, then got back up with 3 fresh hearts.";
            case LIFE_LOST -> {
                int lives = number(event, "lives");
                yield lives == 0 ? "A quiet day cost the last heart."
                        : "A quiet day cost a heart: " + hearts(lives) + " left.";
            }
            case LIFE_GAINED -> "A " + number(event, "streak") + "-day streak won back a heart: "
                    + hearts(number(event, "lives")) + " now.";
            case RESTED -> "A rest day: your hero rested and lost nothing.";
            case CLASS_CHANGED -> "Your class changed from " + className(event.details().get("from")) + " to "
                    + className(event.details().get("to")) + ".";
        };
    }

    private static String subject(HeroEvent latest) {
        return switch (latest.type()) {
            case LEVEL_UP -> "Your hero reached level " + number(latest, "to");
            case LEVEL_DOWN -> "Your hero is back at level " + number(latest, "to");
            case FAINTED -> "Your hero fainted and got back up";
            default -> "Your hero is down to 1 heart"; // the only notable LIFE_LOST
        };
    }

    private static int number(HeroEvent event, String key) {
        return event.details().get(key) instanceof Number number ? number.intValue() : 0;
    }

    private static String hearts(int count) {
        return count + (count == 1 ? " heart" : " hearts");
    }

    /** {@code "ROGUE"} → {@code "Rogue"}. */
    private static String className(Object name) {
        String value = String.valueOf(name);
        return value.isEmpty() ? value : value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    /** @param day the date as the email shows it, e.g. {@code "Tue 27 Oct"} */
    public record Line(LocalDate onDate, String day, String text) {

        static Line of(HeroEvent event) {
            return new Line(event.onDate(), DAY.format(event.onDate()), describe(event));
        }
    }
}

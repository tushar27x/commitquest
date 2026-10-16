// Pure display logic for the Home screen. The backend owns the rules (plan §2); these
// helpers only turn its answers into words and shapes.

import type { Hero, HeroEvent, HeroEvents, LocalDate, WeekStatus, Weekday } from "./api/types";
import { WEEKDAYS } from "./api/types";

const XP_PER_ACTIVE_DAY = 10;
const DAY_MS = 86_400_000;
const MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];

/** `YYYY-MM-DD` as a UTC midnight timestamp, so date maths ignores the browser's zone. */
function dayValue(date: LocalDate): number {
  return Date.parse(`${date}T00:00:00Z`);
}

export function weekdayOf(date: LocalDate): Weekday {
  // getUTCDay: 0 = Sunday; WEEKDAYS starts on Monday.
  return WEEKDAYS[(new Date(dayValue(date)).getUTCDay() + 6) % 7];
}

/** Today is a rest day: no heart at stake (the backend shows `rest` or SLEEPING). */
export function isRestDay(hero: Hero): boolean {
  return hero.mood === "SLEEPING" || hero.rest_days.includes(weekdayOf(hero.today.date));
}

/** "N more active days wins a heart back", or null when hearts are full. */
export function activeDaysToHeart(hero: Hero): number | null {
  if (hero.lives >= hero.max_lives) return null;
  return hero.streak_for_next_life - (hero.streak % hero.streak_for_next_life);
}

/** Active days (10 XP each) until the next level. */
export function activeDaysToLevel(hero: Hero): number {
  return Math.max(1, Math.ceil((hero.xp_for_next - hero.xp) / XP_PER_ACTIVE_DAY));
}

/** What a missed day would cost, for the Today card. */
export function heartsAtStake(lives: number): string {
  if (lives <= 1) return "your last heart";
  if (lives === 2) return "both hearts";
  return `all ${lives} hearts`;
}

/** `h:mm` until `endsAt`, never negative (5:40 LEFT). */
export function timeLeft(endsAt: string, now: number): string {
  const minutes = Math.max(0, Math.floor((Date.parse(endsAt) - now) / 60_000));
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, "0")}`;
}

// The week strip

export interface WeekCell {
  date: LocalDate;
  /** M T W T F S S */
  letter: string;
  status: WeekStatus;
  isToday: boolean;
  mark: string;
  label: string;
}

const WEEK_MARK: Record<WeekStatus, string> = { active: "+10", missed: "-1", today: "?", future: "", rest: "Z" };
const WEEK_LABEL: Record<WeekStatus, string> = {
  active: "active, plus 10 XP",
  missed: "missed, lost a heart",
  today: "today, not fed yet",
  future: "upcoming",
  rest: "rest day",
};

/** Today is found by `today.date`: its status is `today`, or `rest` on a rest day (contract). */
export function weekCells(hero: Hero): WeekCell[] {
  return hero.week.map(({ date, status }) => {
    const isToday = date === hero.today.date;
    // A fed today reads as active, though its XP only lands at the morning check.
    const shown: WeekStatus = isToday && status === "today" && hero.today.active ? "active" : status;
    const weekday = weekdayOf(date);
    const name = weekday.charAt(0) + weekday.slice(1).toLowerCase();
    return {
      date,
      letter: weekday.charAt(0),
      status: shown,
      isToday,
      mark: WEEK_MARK[shown],
      label: `${name}${isToday ? " (today)" : ""}: ${isToday && shown === "active" ? "fed today" : WEEK_LABEL[shown]}`,
    };
  });
}

// The RECENTLY log

export type LogTone = "bone" | "ember" | "gold" | "muted";

export interface LogEntry {
  key: string;
  date: LocalDate;
  day: string;
  text: string;
  tone: LogTone;
}

/** THU within the last week, otherwise 9 OCT. */
export function dayLabel(date: LocalDate, today: LocalDate): string {
  const ago = (dayValue(today) - dayValue(date)) / DAY_MS;
  if (ago >= 0 && ago < 7) return weekdayOf(date).slice(0, 3);
  const d = new Date(dayValue(date));
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
}

const num = (value: unknown): number | null => (typeof value === "number" ? value : null);

function eventText(event: HeroEvent): { text: string; tone: LogTone } {
  const d = event.details;
  switch (event.type) {
    case "LIFE_LOST":
      return { text: "No contributions. Lost a heart.", tone: "ember" };
    case "LIFE_GAINED":
      return { text: "Three days in a row. Won a heart back.", tone: "gold" };
    case "LEVEL_UP":
      return { text: `Reached LV ${num(d.to) ?? "up"}!`, tone: "gold" };
    case "LEVEL_DOWN":
      return { text: `Hearts ran out. Dropped to LV ${num(d.to) ?? "down"}.`, tone: "ember" };
    case "FAINTED":
      return { text: "Hearts ran out. Fainted, then got back up.", tone: "ember" };
    case "RESTED":
      return { text: "Rest day. Nothing lost.", tone: "muted" };
    case "CLASS_CHANGED": {
      const to = typeof d.to === "string" ? d.to : null;
      return { text: to ? `Became a ${to.charAt(0)}${to.slice(1).toLowerCase()}.` : "Changed class.", tone: "bone" };
    }
  }
}

/** XP days and events merged, newest first. On the same day an event comes before the XP line. */
export function recentLog({ events, xp_days }: HeroEvents, today: LocalDate, limit = 4): LogEntry[] {
  const entries: (LogEntry & { order: number })[] = [
    ...xp_days.map((x) => ({
      key: `xp-${x.on_date}`,
      date: x.on_date,
      day: dayLabel(x.on_date, today),
      text: `+${x.xp} XP. Streak ${x.streak}.`,
      tone: "bone" as const,
      order: 0,
    })),
    ...events.map((e, i) => ({
      key: `${e.type}-${e.on_date}`,
      date: e.on_date,
      day: dayLabel(e.on_date, today),
      ...eventText(e),
      order: 1 + i,
    })),
  ];
  return entries
    .sort((a, b) => b.date.localeCompare(a.date) || b.order - a.order)
    .slice(0, limit)
    .map(({ key, date, day, text, tone }) => ({ key, date, day, text, tone }));
}

/** The label over a FAINTED hero: "LV 7 → 6" after a level drop, "LV 1" for a faint at level 1. */
export function faintLabel(hero: Hero, events: HeroEvent[] | undefined): string {
  const last = [...(events ?? [])].reverse().find((e) => e.type === "LEVEL_DOWN" || e.type === "FAINTED");
  if (last?.type === "LEVEL_DOWN") {
    const from = num(last.details.from) ?? hero.level + 1;
    return `LV ${from} → ${num(last.details.to) ?? hero.level}`;
  }
  if (last?.type === "FAINTED" || hero.level === 1) return `LV ${hero.level}`;
  return `LV ${hero.level + 1} → ${hero.level}`;
}

export const TIER_LEVELS: Record<Hero["tier"], string> = {
  CAMPFIRE: "LV 1–4",
  VILLAGE: "LV 5–9",
  KEEP: "LV 10–14",
  CITADEL: "LV 15+",
};

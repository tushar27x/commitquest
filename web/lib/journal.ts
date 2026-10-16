// Pure display logic for the Journal and the Hero sheet.

import type { Archetype, Insights, LocalDate, RepoCount } from "./api/types";
import { CLASS_ORDER } from "./sprites";

const DAY_MS = 86_400_000;
const MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/** The calendar date of an instant in an IANA zone (UTC if the zone is unknown). */
export function localDate(instant: string, timeZone: string): LocalDate {
  const date = new Date(instant);
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
  } catch {
    return date.toISOString().slice(0, 10);
  }
}

function shiftDays(date: LocalDate, days: number): LocalDate {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10);
}

/** 9 OCT */
export function shortDate(date: LocalDate): string {
  const d = new Date(`${date}T00:00:00Z`);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
}

/** Fri 16 Oct, for screen readers and tooltips. */
export function longDate(date: LocalDate): string {
  const d = new Date(`${date}T00:00:00Z`);
  const month = MONTHS[d.getUTCMonth()];
  return `${WEEKDAYS[d.getUTCDay()]} ${d.getUTCDate()} ${month.charAt(0)}${month.slice(1).toLowerCase()}`;
}

export interface CalendarDay {
  date: LocalDate;
  count: number;
  /** 0 = none, 1 = 1–2, 2 = 3–4, 3 = 5+ (the board's four shades). */
  level: 0 | 1 | 2 | 3;
}

/**
 * `last_month_heatmap` is 28 counts, oldest first, ending on the day of `updated_at` in the
 * user's zone. `heatmap_labels` only covers the last 7 days, so the dates are computed here.
 */
export function calendarDays(insights: Insights, timeZone: string): CalendarDay[] {
  const counts = insights.stats.last_month_heatmap;
  const last = localDate(insights.updated_at, timeZone);
  return counts.map((count, i) => ({
    date: shiftDays(last, i - (counts.length - 1)),
    count,
    level: count === 0 ? 0 : count < 3 ? 1 : count < 5 ? 2 : 3,
  }));
}

export interface RepoBar extends RepoCount {
  /** Width of the bar, 0–100, relative to the busiest repo. */
  pct: number;
}

export function repoBars(repos: RepoCount[]): RepoBar[] {
  const max = Math.max(1, ...repos.map((r) => r.count));
  return repos.map((r) => ({ ...r, pct: Math.round((r.count / max) * 100) }));
}

export interface ScoreRow {
  archetype: Archetype;
  score: number;
  /** The top score: the class the habits point to. */
  top: boolean;
}

/** Highest first; ties go to roster order, like the scorer. */
export function scoreRows(scores: Record<Archetype, number>): ScoreRow[] {
  const rows = [...CLASS_ORDER]
    .map((archetype) => ({ archetype, score: scores[archetype] ?? 0 }))
    .sort((a, b) => b.score - a.score || CLASS_ORDER.indexOf(a.archetype) - CLASS_ORDER.indexOf(b.archetype));
  return rows.map((row, i) => ({ ...row, top: i === 0 }));
}

/** MONK -> Monk */
export function className(archetype: Archetype): string {
  return archetype.charAt(0) + archetype.slice(1).toLowerCase();
}

// Pure logic for the Settings screen: the rest-day picker, the timezone list and
// validation errors from `PUT /preferences` (contract.md, "Validation 400s").

import { ApiError } from "./api";
import { WEEKDAYS, type FieldError, type Preferences, type Weekday } from "./api/types";

/** Plan §2: at most 2 rest days (the DB has a CHECK for it too). */
export const MAX_REST_DAYS = 2;

export const WEEKDAY_LETTERS: Record<Weekday, string> = {
  MONDAY: "M",
  TUESDAY: "T",
  WEDNESDAY: "W",
  THURSDAY: "T",
  FRIDAY: "F",
  SATURDAY: "S",
  SUNDAY: "S",
};

/** MONDAY -> Monday */
export function weekdayName(day: Weekday): string {
  return day.charAt(0) + day.slice(1).toLowerCase();
}

/** Monday first, no repeats. */
function normalise(days: readonly Weekday[]): Weekday[] {
  return WEEKDAYS.filter((d) => days.includes(d));
}

/** A day that can't be picked because 2 are already picked. */
export function restDayLocked(days: readonly Weekday[], day: Weekday): boolean {
  return !days.includes(day) && normalise(days).length >= MAX_REST_DAYS;
}

/** Tapping a day: picked days drop out; a third day is refused (the list comes back unchanged). */
export function toggleRestDay(days: readonly Weekday[], day: Weekday): Weekday[] {
  if (days.includes(day)) return normalise(days.filter((d) => d !== day));
  if (restDayLocked(days, day)) return normalise(days);
  return normalise([...days, day]);
}

export function samePreferences(a: Preferences, b: Preferences): boolean {
  return (
    a.timezone === b.timezone &&
    a.alerts_enabled === b.alerts_enabled &&
    a.public_profile === b.public_profile &&
    normalise(a.rest_days).join() === normalise(b.rest_days).join()
  );
}

export interface FieldErrors {
  /** The first message per snake_case field. */
  byField: Partial<Record<keyof Preferences, string>>;
  /** Messages for fields this screen doesn't show, or a 400 without `errors`. */
  other: string[];
}

const FIELDS: readonly string[] = ["timezone", "rest_days", "alerts_enabled", "public_profile"];

/** Splits a validation 400 into per-field messages. Null for any other error. */
export function fieldErrors(error: unknown): FieldErrors | null {
  if (!(error instanceof ApiError) || error.status !== 400) return null;
  const list = (error.problem as { errors?: unknown } | null)?.errors;
  const result: FieldErrors = { byField: {}, other: [] };
  if (!Array.isArray(list) || list.length === 0) {
    result.other.push("The server refused these settings.");
    return result;
  }
  for (const item of list as Partial<FieldError>[]) {
    const message = typeof item?.message === "string" ? item.message : "is invalid";
    const field = typeof item?.field === "string" ? item.field : "";
    if (FIELDS.includes(field)) {
      const key = field as keyof Preferences;
      result.byField[key] ??= message;
    } else {
      result.other.push(field ? `${field}: ${message}` : message);
    }
  }
  return result;
}

/** The browser's IANA zone, or null if it can't tell. */
export function browserTimeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

/**
 * Whether two zone ids name the same zone. Browsers may report a legacy alias
 * (Chrome says Asia/Calcutta for Asia/Kolkata), so compare what Intl resolves each to.
 */
export function sameTimeZone(a: string | null, b: string | null): boolean {
  if (!a || !b) return false;
  if (a === b) return true;
  try {
    const resolve = (zone: string) => new Intl.DateTimeFormat("en", { timeZone: zone }).resolvedOptions().timeZone;
    return resolve(a) === resolve(b);
  } catch {
    return false;
  }
}

export function isValidTimeZone(zone: string): boolean {
  if (!zone) return false;
  try {
    new Intl.DateTimeFormat("en", { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

/** Every IANA zone the browser knows, plus UTC and the given ones, sorted. */
export function timeZoneOptions(...include: (string | null | undefined)[]): string[] {
  let zones: string[] = [];
  try {
    zones = (Intl as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf?.("timeZone") ?? [];
  } catch {
    zones = [];
  }
  const all = new Set([...zones, "UTC", ...include.filter((z): z is string => !!z)]);
  return [...all].sort();
}

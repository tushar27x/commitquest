// "Share private repos" (contract B10/B11): the lock, the warning flow, the re-auth marker and
// the outcome shown after coming back from GitHub. Pure functions, so they are unit tested.

import { ApiError } from "./api";
import { PRIVATE_LOCKED_TITLE, type Preferences } from "./api/types";

type PrivateFields = Pick<Preferences, "include_private" | "private_access" | "private_locked_until">;

/** The lock end as a timestamp, or null when there is none or it has passed. */
export function lockedUntilMs(prefs: Pick<Preferences, "private_locked_until">, now: number): number | null {
  if (!prefs.private_locked_until) return null;
  const until = Date.parse(prefs.private_locked_until);
  return Number.isNaN(until) || until <= now ? null : until;
}

/** Waiting for GitHub (`true`/`none`): turning off changes nothing effective, so the lock allows it. */
export function isWaitingForGithub(prefs: Pick<Preferences, "include_private" | "private_access">): boolean {
  return prefs.include_private && prefs.private_access === "none";
}

/** The switch is disabled by the lock, in both directions (except turning off while waiting). */
export function privateSwitchLocked(prefs: PrivateFields, now: number): boolean {
  if (lockedUntilMs(prefs, now) === null) return false;
  return !isWaitingForGithub(prefs);
}

/** Whether flipping the switch changes the *effective* state: a resync of up to ~90 s, then a 7-day lock. */
export function isEffectiveChange(prefs: Pick<Preferences, "include_private" | "private_access">, includePrivate: boolean): boolean {
  return includePrivate !== prefs.include_private && prefs.private_access === "granted";
}

/** "23 Oct 2026" in the browser's zone. */
export function lockDateLabel(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" }).format(date);
}

/** The lock 429 vs the `preferences` rate limit: both are 429, only the title differs. */
export function isPrivateLockedError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 429 && error.problem?.title === PRIVATE_LOCKED_TITLE;
}

// ---------------------------------------------------------------------------------------------
// The warning screen. Nothing is sent until CONFIRM, and CONFIRM needs the tick.

export type WarningKind = "on" | "off";

export interface WarningState {
  open: WarningKind | null;
  understood: boolean;
}

export type WarningAction = { type: "open"; kind: WarningKind } | { type: "tick"; understood: boolean } | { type: "cancel" } | { type: "confirm" };

export const CLOSED: WarningState = { open: null, understood: false };

/** CONFIRM works only with the tick (the OFF warning needs it too: it is a 7-day lock either way). */
export function canConfirm(state: WarningState): boolean {
  return state.open !== null && state.understood;
}

/**
 * Returns the next state and, only on a valid CONFIRM, the value to send (`include_private`).
 * CANCEL, Esc and a CONFIRM without the tick return `send: null`.
 */
export function warningStep(state: WarningState, action: WarningAction): { state: WarningState; send: boolean | null } {
  switch (action.type) {
    case "open":
      return { state: { open: action.kind, understood: false }, send: null };
    case "tick":
      return { state: state.open ? { ...state, understood: action.understood } : state, send: null };
    case "cancel":
      return { state: CLOSED, send: null };
    case "confirm":
      if (!canConfirm(state)) return { state, send: null };
      return { state: CLOSED, send: state.open === "on" };
  }
}

// ---------------------------------------------------------------------------------------------
// Re-auth marker: the backend's callback redirects to /dashboard#token=… with no hint, so Settings
// leaves a note in sessionStorage before navigating to GitHub (contract, "Login and the lock").

const REAUTH_KEY = "ih_private_reauth";

export function markPrivateReauth(): void {
  try {
    window.sessionStorage.setItem(REAUTH_KEY, "1");
  } catch {
    // Storage blocked: the user simply lands on Home instead of Settings.
  }
}

/** True once, if a private re-auth was started in this tab. */
export function takePrivateReauth(): boolean {
  try {
    const marked = window.sessionStorage.getItem(REAUTH_KEY) === "1";
    if (marked) window.sessionStorage.removeItem(REAUTH_KEY);
    return marked;
  } catch {
    return false;
  }
}

/** Where the token hand-off goes after a private re-auth; Settings reads the outcome and cleans the URL. */
export const PRIVATE_RETURN_PARAM = "private";
export const PRIVATE_RETURN_PATH = `/settings?${PRIVATE_RETURN_PARAM}=back`;

export type ReturnOutcome =
  | { kind: "on" } // granted, access is on
  | { kind: "locked-recorded"; until: string } // GitHub said yes, but the lock kept it off
  | { kind: "declined" } // still waiting: GitHub didn't share it
  | { kind: "none" };

/** What happened at GitHub, from the preferences read after coming back. */
export function returnOutcome(prefs: PrivateFields, now: number): ReturnOutcome {
  if (prefs.include_private && prefs.private_access === "granted") return { kind: "on" };
  if (!prefs.include_private && prefs.private_access === "granted" && prefs.private_locked_until && lockedUntilMs(prefs, now) !== null) {
    return { kind: "locked-recorded", until: prefs.private_locked_until };
  }
  if (prefs.include_private && prefs.private_access === "none") return { kind: "declined" };
  return { kind: "none" };
}

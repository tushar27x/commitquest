import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { PRIVATE_LOCKED_TITLE, type Preferences } from "./api/types";
import {
  CLOSED,
  canConfirm,
  isEffectiveChange,
  isPrivateLockedError,
  isWaitingForGithub,
  lockDateLabel,
  lockedUntilMs,
  markPrivateReauth,
  privateSwitchLocked,
  returnOutcome,
  takePrivateReauth,
  warningStep,
  type WarningState,
} from "./private";

const NOW = Date.parse("2026-10-16T09:30:00Z");
const LOCK = "2026-10-23T09:30:00Z";
type P = Pick<Preferences, "include_private" | "private_access" | "private_locked_until">;
const p = (include_private: boolean, private_access: "none" | "granted", private_locked_until: string | null = null): P => ({ include_private, private_access, private_locked_until });

describe("the lock", () => {
  it("is active only while the instant is in the future", () => {
    expect(lockedUntilMs(p(true, "granted", LOCK), NOW)).toBe(Date.parse(LOCK));
    expect(lockedUntilMs(p(true, "granted", LOCK), Date.parse(LOCK))).toBeNull();
    expect(lockedUntilMs(p(true, "granted", null), NOW)).toBeNull();
    expect(lockedUntilMs(p(true, "granted", "garbage"), NOW)).toBeNull();
  });

  it("disables the switch in both directions", () => {
    expect(privateSwitchLocked(p(true, "granted", LOCK), NOW)).toBe(true);
    expect(privateSwitchLocked(p(false, "granted", LOCK), NOW)).toBe(true);
    expect(privateSwitchLocked(p(false, "none", LOCK), NOW)).toBe(true);
  });

  it("allows turning off while waiting for GitHub, the contract's one exception", () => {
    expect(isWaitingForGithub(p(true, "none"))).toBe(true);
    expect(privateSwitchLocked(p(true, "none", LOCK), NOW)).toBe(false);
  });

  it("leaves the switch alone without a lock or after it passed", () => {
    expect(privateSwitchLocked(p(false, "granted"), NOW)).toBe(false);
    expect(privateSwitchLocked(p(true, "granted", LOCK), Date.parse(LOCK) + 1)).toBe(false);
  });

  it("formats the date in the browser's zone and survives a bad value", () => {
    expect(lockDateLabel("2026-10-23T12:00:00Z")).toBe("23 Oct 2026");
    expect(lockDateLabel("nope")).toBe("");
  });

  it("tells the lock 429 from the rate limit by the title", () => {
    const problem = (title: string) => ({ type: "about:blank", title, status: 429, detail: "", instance: "/" });
    expect(isPrivateLockedError(new ApiError(429, "x", 60, problem(PRIVATE_LOCKED_TITLE)))).toBe(true);
    expect(isPrivateLockedError(new ApiError(429, "x", 60, problem("Too Many Requests")))).toBe(false);
    expect(isPrivateLockedError(new ApiError(409, "x", null, problem(PRIVATE_LOCKED_TITLE)))).toBe(false);
    expect(isPrivateLockedError(new Error("x"))).toBe(false);
  });
});

describe("effective change", () => {
  it("is a flip while GitHub has shared the scope (a resync, then the lock)", () => {
    expect(isEffectiveChange(p(false, "granted"), true)).toBe(true);
    expect(isEffectiveChange(p(true, "granted"), false)).toBe(true);
  });
  it("is not a flip that only records the wish, or no flip at all", () => {
    expect(isEffectiveChange(p(false, "none"), true)).toBe(false);
    expect(isEffectiveChange(p(true, "none"), false)).toBe(false);
    expect(isEffectiveChange(p(true, "granted"), true)).toBe(false);
  });
});

describe("the warning screen", () => {
  const open = (kind: "on" | "off"): WarningState => warningStep(CLOSED, { type: "open", kind }).state;

  it("opens unticked and sends nothing", () => {
    const step = warningStep(CLOSED, { type: "open", kind: "on" });
    expect(step).toEqual({ state: { open: "on", understood: false }, send: null });
    expect(canConfirm(step.state)).toBe(false);
  });

  it("CONFIRM without the tick sends nothing and stays open, for ON and OFF", () => {
    for (const kind of ["on", "off"] as const) {
      const state = open(kind);
      expect(warningStep(state, { type: "confirm" })).toEqual({ state, send: null });
    }
  });

  it("the tick enables CONFIRM, and unticking disables it again", () => {
    const ticked = warningStep(open("on"), { type: "tick", understood: true }).state;
    expect(canConfirm(ticked)).toBe(true);
    expect(canConfirm(warningStep(ticked, { type: "tick", understood: false }).state)).toBe(false);
  });

  it("CONFIRM after the tick sends the new value once and closes", () => {
    const on = warningStep(warningStep(open("on"), { type: "tick", understood: true }).state, { type: "confirm" });
    expect(on).toEqual({ state: CLOSED, send: true });
    const off = warningStep(warningStep(open("off"), { type: "tick", understood: true }).state, { type: "confirm" });
    expect(off).toEqual({ state: CLOSED, send: false });
    // A second CONFIRM on the closed screen (a double tap) sends nothing.
    expect(warningStep(on.state, { type: "confirm" }).send).toBeNull();
  });

  it("CANCEL (and Esc) closes without sending, even when ticked", () => {
    const ticked = warningStep(open("on"), { type: "tick", understood: true }).state;
    expect(warningStep(ticked, { type: "cancel" })).toEqual({ state: CLOSED, send: null });
  });

  it("a new warning starts unticked", () => {
    const ticked = warningStep(open("on"), { type: "tick", understood: true }).state;
    expect(warningStep(warningStep(ticked, { type: "cancel" }).state, { type: "open", kind: "off" }).state.understood).toBe(false);
  });
});

describe("the re-auth marker", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("is taken once", () => {
    const store = new Map<string, string>();
    vi.stubGlobal("window", {
      sessionStorage: {
        getItem: (k: string) => store.get(k) ?? null,
        setItem: (k: string, v: string) => void store.set(k, v),
        removeItem: (k: string) => void store.delete(k),
      },
    });
    expect(takePrivateReauth()).toBe(false);
    markPrivateReauth();
    expect(takePrivateReauth()).toBe(true);
    expect(takePrivateReauth()).toBe(false);
  });

  it("survives blocked storage", () => {
    vi.stubGlobal("window", {
      get sessionStorage(): never {
        throw new Error("blocked");
      },
    });
    expect(() => markPrivateReauth()).not.toThrow();
    expect(takePrivateReauth()).toBe(false);
  });
});

describe("the outcome after coming back from GitHub", () => {
  it("granted and on", () => {
    expect(returnOutcome(p(true, "granted", LOCK), NOW)).toEqual({ kind: "on" });
  });
  it("locked: the permission was recorded but access stays off", () => {
    expect(returnOutcome(p(false, "granted", LOCK), NOW)).toEqual({ kind: "locked-recorded", until: LOCK });
  });
  it("still waiting: GitHub did not share it", () => {
    expect(returnOutcome(p(true, "none"), NOW)).toEqual({ kind: "declined" });
  });
  it("nothing to say otherwise", () => {
    expect(returnOutcome(p(false, "none"), NOW)).toEqual({ kind: "none" });
    expect(returnOutcome(p(false, "granted"), NOW)).toEqual({ kind: "none" });
  });
});

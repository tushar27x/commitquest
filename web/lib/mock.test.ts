import { afterEach, describe, expect, it, vi } from "vitest";
import heroFixture from "../public/fixtures/hero.json";
import variantsFixture from "../public/fixtures/hero-variants.json";
import prefsVariants from "../public/fixtures/preferences-variants.json";
import type { Hero, Preferences } from "./api/types";
import { applyPreferencesVariant, applyVariant, decidePreferencesPut, isIanaZone, servedByMock } from "./mock";

const hero = heroFixture as Hero;
const variants = variantsFixture as Record<string, unknown>;

describe("applyVariant", () => {
  it("returns hero.json without a variant", () => {
    expect(applyVariant(hero, variants, null)).toBe(hero);
  });

  it("merges a variant over hero.json", () => {
    const happy = applyVariant(hero, variants, "happy");
    expect(happy).toMatchObject({ lives: 3, mood: "HAPPY", level: 7 });
    expect(happy.today.active).toBe(true);
  });

  it("replaces nested objects whole", () => {
    expect(applyVariant(hero, variants, "class_cooldown").class).toMatchObject({
      chosen: "ROGUE",
      current: "ROGUE",
      can_switch_at: "2026-10-21T09:00:00Z",
    });
  });

  it("ignores unknown variants and the _doc key", () => {
    expect(applyVariant(hero, variants, "nope")).toBe(hero);
    expect(applyVariant(hero, variants, "_doc")).toBe(hero);
  });
});

describe("servedByMock (NEXT_PUBLIC_MOCK unset)", () => {
  it("sends every endpoint to the backend", () => {
    expect(servedByMock("/api/user/hero/class", "PUT")).toBe(false);
    expect(servedByMock("/api/user/hero")).toBe(false);
    expect(servedByMock("/api/user/hero/events?days=30")).toBe(false);
    expect(servedByMock("/api/user/preferences")).toBe(false);
    expect(servedByMock("/api/user/preferences", "PUT")).toBe(false);
    expect(servedByMock("/api/user/insights")).toBe(false);
    expect(servedByMock("/api/user/sync")).toBe(false);
    expect(servedByMock("/api/user/heroes")).toBe(false);
  });
});

describe("mockFetch: class change and preferences (B5/B6 rules)", () => {
  // Fresh module per test: the mock keeps class changes and saved preferences in memory.
  async function load() {
    vi.resetModules();
    const fixtures: Record<string, unknown> = {
      hero: heroFixture,
      "hero-variants": variantsFixture,
      "hero-events": (await import("../public/fixtures/hero-events.json")).default,
      preferences: (await import("../public/fixtures/preferences.json")).default,
      "preferences-variants": prefsVariants,
    };
    vi.stubGlobal("fetch", async (url: string) => {
      const name = /\/fixtures\/(.+)\.json$/.exec(url)?.[1] ?? "";
      return new Response(JSON.stringify(fixtures[name]), { status: fixtures[name] ? 200 : 404 });
    });
    return (await import("./mock")).mockFetch;
  }
  const put = (body: unknown) => ({ method: "PUT", body: JSON.stringify(body) });

  afterEach(() => vi.unstubAllGlobals());

  it("pins the followed class: CLASS_CHANGED from == to, then the cooldown runs", async () => {
    const mockFetch = await load();
    const res = await mockFetch("/api/user/hero/class", put({ archetype: "MONK" }));
    expect(res.status).toBe(200);
    const updated = (await res.json()) as Hero;
    expect(updated.class).toMatchObject({ chosen: "MONK", current: "MONK" });
    expect(updated.class.can_switch_at).not.toBeNull();

    const events = await (await mockFetch("/api/user/hero/events?days=30")).json();
    expect(events.events.at(-1)).toEqual({
      type: "CLASS_CHANGED",
      on_date: hero.today.date,
      details: { from: "MONK", to: "MONK", chosen: "MONK" },
    });

    const again = await mockFetch("/api/user/hero/class", put({ archetype: "ROGUE" }));
    expect(again.status).toBe(429);
    expect(Number(again.headers.get("Retry-After"))).toBeGreaterThan(6 * 86_400);
  });

  it("answers a no-op with 200 and no cooldown", async () => {
    const mockFetch = await load();
    for (const body of [{ archetype: null }, {}]) {
      const res = await mockFetch("/api/user/hero/class", put(body));
      expect(res.status).toBe(200);
      expect(((await res.json()) as Hero).class.can_switch_at).toBeNull();
    }
  });

  it("refuses a class that doesn't qualify with 409", async () => {
    const mockFetch = await load();
    expect((await mockFetch("/api/user/hero/class", put({ archetype: "CLERIC" }))).status).toBe(409);
  });

  it("answers 409 before 429 when a class is not qualified and the cooldown runs", async () => {
    const mockFetch = await load();
    expect((await mockFetch("/api/user/hero/class", put({ archetype: "MONK" }))).status).toBe(200);
    expect((await mockFetch("/api/user/hero/class", put({ archetype: "CLERIC" }))).status).toBe(409);
    expect((await mockFetch("/api/user/hero/class", put({ archetype: null }))).status).toBe(429);
  });

  it("validates PUT /preferences like the backend", async () => {
    const mockFetch = await load();
    const bad = await mockFetch(
      "/api/user/preferences",
      put({ timezone: "GMT+5", rest_days: ["MONDAY", "MONDAY", "FRIDAY"], alerts_enabled: true }),
    );
    expect(bad.status).toBe(400);
    const { errors } = await bad.json();
    expect(errors).toEqual(
      expect.arrayContaining([
        { field: "public_profile", message: "must not be null" },
        { field: "include_private", message: "must not be null" },
        { field: "timezone", message: "must be an IANA time zone, like Europe/Berlin" },
        { field: "rest_days", message: "at most 2 rest days" },
        { field: "rest_days", message: "must not repeat a day" },
      ]),
    );

    const good = { timezone: "Europe/Berlin", rest_days: ["SATURDAY"], alerts_enabled: false, public_profile: true, include_private: true };
    expect((await mockFetch("/api/user/preferences", put(good))).status).toBe(200);
    // B10: private_access is forced to none, even if the body says granted.
    const granted = { ...good, private_access: "granted" };
    expect(await (await mockFetch("/api/user/preferences", put(granted))).json()).toEqual({ ...good, private_access: "none", private_locked_until: null });
    expect(await (await mockFetch("/api/user/preferences")).json()).toEqual({ ...good, private_access: "none", private_locked_until: null });
  });
});

describe("mockFetch: ?mock_error (B38)", () => {
  async function load(search: string) {
    vi.resetModules();
    vi.stubGlobal("window", { location: { search } });
    vi.stubGlobal("fetch", async () => new Response(JSON.stringify(heroFixture), { status: 200 }));
    return (await import("./mock")).mockFetch;
  }

  afterEach(() => vi.unstubAllGlobals());

  it("fails the first call of the page load only", async () => {
    const mockFetch = await load("?mock_error=401");
    expect((await mockFetch("/api/user/hero")).status).toBe(401);
    expect((await mockFetch("/api/user/hero")).status).toBe(200);
  });

  it("keeps the error for the refetch when the first fetch is aborted (StrictMode double mount)", async () => {
    const mockFetch = await load("?mock_error=429");
    const first = new AbortController();
    const cancelled = mockFetch("/api/user/hero", { signal: first.signal });
    // The cleanup aborts in the same task, before the response is delivered; the remount fetches again.
    first.abort();
    await expect(cancelled).rejects.toBeDefined();

    const again = await mockFetch("/api/user/hero", { signal: new AbortController().signal });
    expect(again.status).toBe(429);
    expect(again.headers.get("Retry-After")).toBe("451");
    expect((await mockFetch("/api/user/hero")).status).toBe(200);
  });

  it("fails only one of two concurrent calls", async () => {
    const mockFetch = await load("?mock_error=502");
    const statuses = (await Promise.all([mockFetch("/api/user/hero"), mockFetch("/api/user/insights")])).map((r) => r.status);
    expect(statuses).toEqual([502, 200]);
  });

  it("ignores a status outside 400-599", async () => {
    const mockFetch = await load("?mock_error=200");
    expect((await mockFetch("/api/user/hero")).status).toBe(200);
  });
});

describe("mockFetch: the Arena", () => {
  async function load(search: string) {
    vi.resetModules();
    vi.stubGlobal("window", { location: { search } });
    const fixtures: Record<string, unknown> = {
      leaderboard: (await import("../public/fixtures/leaderboard.json")).default,
      rank: (await import("../public/fixtures/rank.json")).default,
      "rank-not-in-arena": (await import("../public/fixtures/rank-not-in-arena.json")).default,
    };
    vi.stubGlobal("fetch", async (url: string) => {
      const name = /\/fixtures\/(.+)\.json$/.exec(url)?.[1] ?? "";
      return new Response(JSON.stringify(fixtures[name]), { status: fixtures[name] ? 200 : 404 });
    });
    return (await import("./mock")).mockFetch;
  }

  afterEach(() => vi.unstubAllGlobals());

  it("serves the MONK board and the caller's rank", async () => {
    const mockFetch = await load("");
    const board = await (await mockFetch("/api/leaderboard?archetype=MONK&page=0")).json();
    expect(board).toMatchObject({ archetype: "MONK", total: 6 });
    expect(board.entries.some((e: { is_you: boolean }) => e.is_you)).toBe(true);
    expect(await (await mockFetch("/api/user/rank")).json()).toMatchObject({ rank: 4, top_percent: 67 });
  });

  it("shows other classes, and ?mock_arena=empty, as empty boards", async () => {
    const other = await (await (await load(""))("/api/leaderboard?archetype=ROGUE&page=0")).json();
    expect(other).toMatchObject({ archetype: "ROGUE", total: 0, entries: [] });
    const empty = await (await (await load("?mock_arena=empty"))("/api/leaderboard?archetype=MONK&page=0")).json();
    expect(empty.total).toBe(0);
  });

  it("?mock_arena=hidden answers the 'Not in the Arena' 404", async () => {
    const res = await (await load("?mock_arena=hidden"))("/api/user/rank");
    expect(res.status).toBe(404);
    expect((await res.json()).title).toBe("Not in the Arena");
  });
});

describe("isIanaZone (mock of the backend's @IanaTimeZone)", () => {
  it("accepts region ids, aliases and Etc zones", () => {
    for (const zone of ["Europe/Berlin", "Asia/Kolkata", "Asia/Calcutta", "Etc/GMT+5", "UTC"]) expect(isIanaZone(zone)).toBe(true);
  });

  it("refuses offsets and unknown ids", () => {
    for (const zone of ["+05:30", "-03:00", "GMT+5", "UTC-3", "Mars/Olympus", ""]) expect(isIanaZone(zone)).toBe(false);
  });
});

describe("applyPreferencesVariant (B11)", () => {
  const base: Preferences = { timezone: "UTC", rest_days: [], alerts_enabled: true, public_profile: false, include_private: false, private_access: "none", private_locked_until: null };
  const variants = prefsVariants as Record<string, unknown>;
  it("merges every state of preferences-variants.json over the base", () => {
    expect(applyPreferencesVariant(base, variants, "private_off_granted")).toMatchObject({ include_private: false, private_access: "granted", private_locked_until: null });
    expect(applyPreferencesVariant(base, variants, "private_granted")).toMatchObject({ include_private: true, private_access: "granted" });
    expect(applyPreferencesVariant(base, variants, "private_reauth")).toMatchObject({ include_private: true, private_access: "none" });
    expect(applyPreferencesVariant(base, variants, "private_locked_on")).toMatchObject({ include_private: true, private_access: "granted", private_locked_until: "2026-10-23T09:30:00Z" });
    expect(applyPreferencesVariant(base, variants, "private_locked_off")).toMatchObject({ include_private: false, private_access: "granted", private_locked_until: "2026-10-23T09:30:00Z" });
    expect(applyPreferencesVariant(base, variants, "private_lock_lost_scope")).toMatchObject({ include_private: true, private_access: "none", private_locked_until: "2026-10-23T09:30:00Z" });
  });
  it("leaves other variants alone", () => {
    expect(applyPreferencesVariant(base, variants, null)).toBe(base);
    expect(applyPreferencesVariant(base, variants, "happy")).toBe(base);
    expect(applyPreferencesVariant(base, variants, "_doc")).toBe(base);
  });
});

describe("decidePreferencesPut (B11 order of checks)", () => {
  const NOW = Date.parse("2026-10-16T09:30:00Z");
  const LOCK = "2026-10-23T09:30:00Z";
  const prefs = (patch: Partial<Preferences>): Preferences => ({
    timezone: "UTC",
    rest_days: [],
    alerts_enabled: true,
    public_profile: false,
    include_private: false,
    private_access: "granted",
    private_locked_until: null,
    ...patch,
  });

  it("refuses a change while locked, in both directions, with the seconds left", () => {
    const off = decidePreferencesPut(prefs({ include_private: true, private_locked_until: LOCK }), prefs({ include_private: false }), NOW);
    expect(off).toEqual({ kind: "locked", retryAfter: 7 * 86_400 });
    const on = decidePreferencesPut(prefs({ include_private: false, private_locked_until: LOCK }), prefs({ include_private: true }), NOW);
    expect(on.kind).toBe("locked");
  });
  it("allows the same value while locked, and turning off while waiting for GitHub", () => {
    const same = decidePreferencesPut(prefs({ private_locked_until: LOCK }), prefs({ alerts_enabled: false }), NOW);
    expect(same).toMatchObject({ kind: "saved", resync: false });
    const waiting = decidePreferencesPut(
      prefs({ include_private: true, private_access: "none", private_locked_until: LOCK }),
      prefs({ include_private: false }),
      NOW,
    );
    expect(waiting).toMatchObject({ kind: "saved", resync: false, saved: { include_private: false, private_access: "none", private_locked_until: LOCK } });
  });
  it("lets a past lock go", () => {
    expect(decidePreferencesPut(prefs({ private_locked_until: "2026-10-01T00:00:00Z" }), prefs({ include_private: true }), NOW).kind).toBe("saved");
  });
  it("resyncs and locks for 7 days on an effective flip, and ignores the read-only fields", () => {
    const body = prefs({ include_private: true, private_access: "none", private_locked_until: "2000-01-01T00:00:00Z" });
    const flip = decidePreferencesPut(prefs({}), body, NOW);
    expect(flip).toMatchObject({ kind: "saved", resync: true, saved: { include_private: true, private_access: "granted", private_locked_until: "2026-10-23T09:30:00.000Z" } });
  });
  it("only records the wish while GitHub has not shared the scope: no resync, no lock", () => {
    const wish = decidePreferencesPut(prefs({ private_access: "none" }), prefs({ include_private: true }), NOW);
    expect(wish).toMatchObject({ kind: "saved", resync: false, saved: { include_private: true, private_access: "none", private_locked_until: null } });
  });
});

describe("mockFetch: the private lock (B11)", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  async function load(variant: string) {
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_MOCK", "1");
    const fixtures: Record<string, unknown> = {
      preferences: (await import("../public/fixtures/preferences.json")).default,
      "preferences-variants": prefsVariants,
    };
    vi.stubGlobal("window", { location: { search: `?mock=${variant}` } });
    vi.stubGlobal("fetch", async (url: string) => {
      const name = /\/fixtures\/(.+)\.json$/.exec(url)?.[1] ?? "";
      return new Response(JSON.stringify(fixtures[name]), { status: 200 });
    });
    return (await import("./mock")).mockFetch;
  }
  const put = (body: unknown) => ({ method: "PUT", body: JSON.stringify(body) });

  it("answers the lock 429 with its own title and Retry-After, not the rate-limit title", async () => {
    const mockFetch = await load("private_locked_on");
    const served = await (await mockFetch("/api/user/preferences")).json();
    expect(served.private_locked_until).toBe("2026-10-23T09:30:00Z");
    const res = await mockFetch("/api/user/preferences", put({ ...served, include_private: false }));
    expect(res.status).toBe(429);
    expect((await res.json()).title).toBe("Private access locked");
    expect(Number(res.headers.get("Retry-After"))).toBeGreaterThan(6 * 86_400);
  });

  it("an effective flip takes 3 s, then answers the new state with a lock 7 days ahead", async () => {
    const mockFetch = await load("private_off_granted");
    const served = await (await mockFetch("/api/user/preferences")).json();
    vi.useFakeTimers();
    let done = false;
    const pending = mockFetch("/api/user/preferences", put({ ...served, include_private: true })).then((r) => {
      done = true;
      return r;
    });
    await vi.advanceTimersByTimeAsync(2_900);
    expect(done).toBe(false);
    await vi.advanceTimersByTimeAsync(200);
    const saved = await (await pending).json();
    expect(saved).toMatchObject({ include_private: true, private_access: "granted" });
    expect(Date.parse(saved.private_locked_until) - Date.parse("2026-10-16T12:50:00Z")).toBeGreaterThan(6.9 * 86_400_000);
    // The lock now holds for the next change too.
    expect((await mockFetch("/api/user/preferences", put({ ...saved, include_private: false }))).status).toBe(429);
  });
});

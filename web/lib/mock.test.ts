import { afterEach, describe, expect, it, vi } from "vitest";
import heroFixture from "../public/fixtures/hero.json";
import variantsFixture from "../public/fixtures/hero-variants.json";
import type { Hero } from "./api/types";
import { applyVariant, isIanaZone, servedByMock } from "./mock";

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
  it("serves the endpoints the backend hasn't built yet", () => {
    expect(servedByMock("/api/user/hero/class", "PUT")).toBe(true);
    expect(servedByMock("/api/user/hero/class", "put")).toBe(true);
  });

  it("sends the existing endpoints to the backend", () => {
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
        { field: "timezone", message: "must be an IANA time zone, like Europe/Berlin" },
        { field: "rest_days", message: "at most 2 rest days" },
        { field: "rest_days", message: "must not repeat a day" },
      ]),
    );

    const good = { timezone: "Europe/Berlin", rest_days: ["SATURDAY"], alerts_enabled: false, public_profile: true };
    expect((await mockFetch("/api/user/preferences", put(good))).status).toBe(200);
    expect(await (await mockFetch("/api/user/preferences")).json()).toEqual(good);
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

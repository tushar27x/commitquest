import { describe, expect, it } from "vitest";
import heroFixture from "../public/fixtures/hero.json";
import variantsFixture from "../public/fixtures/hero-variants.json";
import type { Hero } from "./api/types";
import { applyVariant, servedByMock } from "./mock";

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
    expect(servedByMock("/api/user/hero")).toBe(true);
    expect(servedByMock("/api/user/hero/events?days=30")).toBe(true);
    expect(servedByMock("/api/user/preferences")).toBe(true);
  });

  it("sends the existing endpoints to the backend", () => {
    expect(servedByMock("/api/user/insights")).toBe(false);
    expect(servedByMock("/api/user/sync")).toBe(false);
    expect(servedByMock("/api/user/heroes")).toBe(false);
  });
});

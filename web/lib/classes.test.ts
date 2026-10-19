import { describe, expect, it } from "vitest";
import heroFixture from "../public/fixtures/hero.json";
import insightsFixture from "../public/fixtures/insights.json";
import type { Hero, HeroClass, Insights } from "./api/types";
import { CLASS_ORDER } from "./sprites";
import { CLASS_SCORING, classAction, classOptions, classSuggestion, cooldownLeft, qualifiedClasses } from "./classes";

const hero = heroFixture as Hero;
const scores = (insightsFixture as Insights).archetype_scores;
const following: HeroClass = hero.class; // chosen null, MONK top
const pinnedRogue: HeroClass = { ...following, chosen: "ROGUE", current: "ROGUE" };

describe("classAction", () => {
  it("is a no-op for the stored choice", () => {
    expect(classAction(following, null)).toBe("none");
    expect(classAction(pinnedRogue, "ROGUE")).toBe("none");
  });

  it("pins the top class when following it", () => {
    expect(classAction(following, "MONK")).toBe("pin");
  });

  it("becomes another class", () => {
    expect(classAction(following, "ROGUE")).toBe("become");
    expect(classAction(pinnedRogue, "MONK")).toBe("become");
  });

  it("goes back to following the top score", () => {
    expect(classAction(pinnedRogue, null)).toBe("follow");
  });
});

describe("classOptions", () => {
  it("lists by score with the design's tags", () => {
    const rows = classOptions(scores, following);
    expect(rows.map((r) => [r.archetype, r.score, r.tag])).toEqual([
      ["MONK", 52, "CURRENT"],
      ["ROGUE", 47, "QUALIFIED"],
      ["PALADIN", 44, "QUALIFIED"],
      ["BARBARIAN", 44, "QUALIFIED"],
      ["WIZARD", 33, "NEEDS 40"],
      ["ARTIFICER", 33, "NEEDS 40"],
      ["BARD", 10, "NEEDS 40"],
      ["CLERIC", 0, "NEEDS 40"],
    ]);
    expect(rows.filter((r) => r.selectable).map((r) => r.archetype)).toEqual(["MONK", "ROGUE", "PALADIN", "BARBARIAN"]);
  });

  it("keeps a chosen class that fell below 40 selectable", () => {
    const cls: HeroClass = { ...following, chosen: "WIZARD", current: "WIZARD" };
    const wizard = classOptions(scores, cls).find((r) => r.archetype === "WIZARD")!;
    expect(wizard).toMatchObject({ current: true, qualified: false, selectable: true, tag: "CURRENT" });
    expect(classOptions(scores, cls)[0].tag).toBe("TOP SCORE");
  });
});

describe("qualifiedClasses", () => {
  it("is score ≥ 40 plus the top class", () => {
    expect(qualifiedClasses(scores)).toEqual(["PALADIN", "BARBARIAN", "MONK", "ROGUE"]);
    const low = { ...scores, MONK: 30, ROGUE: 20, PALADIN: 10, BARBARIAN: 5 };
    expect(qualifiedClasses(low)).toEqual(["WIZARD"]);
  });
});

describe("classSuggestion", () => {
  it("only shows for a chosen class that isn't the top score", () => {
    expect(classSuggestion(following, scores)).toBeNull();
    expect(classSuggestion({ ...following, chosen: "MONK" }, scores)).toBeNull();
    expect(classSuggestion(pinnedRogue, scores)).toEqual({ scored: "MONK", belowThreshold: false });
    expect(classSuggestion({ ...following, chosen: "BARD", current: "BARD" }, scores)).toEqual({ scored: "MONK", belowThreshold: true });
  });
});

describe("cooldownLeft", () => {
  const now = Date.parse("2026-10-16T12:50:00Z");
  it("counts down to can_switch_at", () => {
    expect(cooldownLeft({ ...following, can_switch_at: "2026-10-16T13:50:00Z" }, now)).toBe(3_600_000);
    expect(cooldownLeft({ ...following, can_switch_at: "2026-10-15T00:00:00Z" }, now)).toBe(0);
    expect(cooldownLeft(following, now)).toBe(0);
  });

  it("takes a later local deadline (a 429's Retry-After)", () => {
    expect(cooldownLeft(following, now, now + 5_000)).toBe(5_000);
  });
});

describe("CLASS_SCORING", () => {
  it("covers all 8 classes, in roster order", () => {
    expect(Object.keys(CLASS_SCORING)).toEqual(CLASS_ORDER);
  });

  it("has weights adding up to 100 and a tip for every class", () => {
    for (const [archetype, { signals, tip }] of Object.entries(CLASS_SCORING)) {
      expect(signals.reduce((sum, s) => sum + s.weight, 0), archetype).toBe(100);
      expect(tip, archetype).not.toBe("");
    }
  });
});

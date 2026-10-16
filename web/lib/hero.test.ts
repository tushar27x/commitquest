import { describe, expect, it } from "vitest";
import heroFixture from "../public/fixtures/hero.json";
import eventsFixture from "../public/fixtures/hero-events.json";
import variantsFixture from "../public/fixtures/hero-variants.json";
import type { Hero, HeroEvents } from "./api/types";
import {
  activeDaysToHeart,
  activeDaysToLevel,
  dayLabel,
  faintLabel,
  heartsAtStake,
  isRestDay,
  recentLog,
  timeLeft,
  weekCells,
  weekdayOf,
} from "./hero";
import { applyVariant } from "./mock";

const hero = heroFixture as Hero;
const events = eventsFixture as HeroEvents;
const variant = (name: string) => applyVariant(hero, variantsFixture as Record<string, unknown>, name);

describe("fixture story (Friday, hungry, 2 hearts)", () => {
  it("counts days to the next heart and level", () => {
    expect(activeDaysToHeart(hero)).toBe(2);
    expect(activeDaysToLevel(hero)).toBe(21);
    expect(activeDaysToHeart(variant("happy"))).toBeNull();
  });

  it("knows which days are rest days", () => {
    expect(weekdayOf("2026-10-16")).toBe("FRIDAY");
    expect(weekdayOf("2026-10-18")).toBe("SUNDAY");
    expect(isRestDay(hero)).toBe(false);
    expect(isRestDay(variant("sleeping"))).toBe(true);
  });

  it("shows 5:40 left at 18:20 local", () => {
    expect(timeLeft(hero.today.day_ends_at, Date.parse("2026-10-16T12:50:00Z"))).toBe("5:40");
    expect(timeLeft(hero.today.day_ends_at, Date.parse("2026-10-17T00:00:00Z"))).toBe("0:00");
  });

  it("words the hearts at stake", () => {
    expect(heartsAtStake(1)).toBe("your last heart");
    expect(heartsAtStake(2)).toBe("both hearts");
    expect(heartsAtStake(3)).toBe("all 3 hearts");
  });
});

describe("weekCells", () => {
  it("marks M T W T F S S like the design", () => {
    const cells = weekCells(hero);
    expect(cells.map((c) => c.letter).join("")).toBe("MTWTFSS");
    expect(cells.map((c) => c.mark)).toEqual(["+10", "+10", "-1", "+10", "?", "", "Z"]);
    expect(cells.filter((c) => c.isToday).map((c) => c.date)).toEqual(["2026-10-16"]);
  });

  it("finds today by date on a rest day", () => {
    const cells = weekCells(variant("sleeping"));
    const today = cells.find((c) => c.isToday);
    expect(today).toMatchObject({ date: "2026-10-18", status: "rest", mark: "Z" });
  });

  it("shows a fed today as active", () => {
    expect(weekCells(variant("happy")).find((c) => c.isToday)).toMatchObject({ status: "active", mark: "+10" });
  });
});

describe("recentLog", () => {
  it("lists newest first, like the design", () => {
    const log = recentLog(events, "2026-10-16");
    expect(log.map((e) => [e.day, e.text, e.tone])).toEqual([
      ["THU", "+10 XP. Streak 1.", "bone"],
      ["WED", "No contributions. Lost a heart.", "ember"],
      ["TUE", "+10 XP. Streak 2.", "bone"],
      ["MON", "+10 XP. Streak 1.", "bone"],
    ]);
  });

  it("puts an event before that day's XP line", () => {
    const log = recentLog(
      { events: [{ type: "LEVEL_UP", on_date: "2026-10-15", details: { from: 7, to: 8 } }], xp_days: [{ on_date: "2026-10-15", xp: 10, streak: 2 }] },
      "2026-10-16",
    );
    expect(log.map((e) => e.text)).toEqual(["Reached LV 8!", "+10 XP. Streak 2."]);
  });

  it("labels older days by date", () => {
    expect(dayLabel("2026-10-09", "2026-10-16")).toBe("9 OCT");
    expect(dayLabel("2026-10-10", "2026-10-16")).toBe("SAT");
  });
});

describe("faintLabel", () => {
  it("shows the level drop from the event", () => {
    const h = { ...hero, level: 6 };
    expect(faintLabel(h, [{ type: "LEVEL_DOWN", on_date: "2026-10-15", details: { from: 7, to: 6 } }])).toBe("LV 7 → 6");
  });

  it("falls back to the level when there is no event", () => {
    expect(faintLabel({ ...hero, level: 1 }, [])).toBe("LV 1");
    expect(faintLabel({ ...hero, level: 6 }, undefined)).toBe("LV 7 → 6");
  });
});

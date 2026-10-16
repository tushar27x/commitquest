import { describe, expect, it } from "vitest";
import insightsFixture from "../public/fixtures/insights.json";
import type { Insights } from "./api/types";
import { calendarDays, localDate, longDate, repoBars, scoreRows, shortDate } from "./journal";

const insights = insightsFixture as Insights;

describe("calendarDays", () => {
  it("ends on the day of updated_at in the user's zone", () => {
    const days = calendarDays(insights, "Asia/Kolkata");
    expect(days).toHaveLength(28);
    expect(days[27].date).toBe("2026-10-16");
    expect(days[0].date).toBe("2026-09-19");
  });

  it("agrees with heatmap_labels for the last 7 days", () => {
    const last7 = calendarDays(insights, "Asia/Kolkata").slice(-7).map((d) => longDate(d.date).slice(0, 3));
    expect(last7).toEqual(insights.stats.heatmap_labels);
  });

  it("buckets counts into the four shades", () => {
    const levels = calendarDays(insights, "UTC").map((d) => d.level);
    expect(levels.slice(0, 5)).toEqual([1, 3, 0, 2, 1]);
  });

  it("crosses midnight by zone", () => {
    expect(localDate("2026-10-16T20:00:00Z", "Asia/Kolkata")).toBe("2026-10-17");
    expect(localDate("2026-10-16T20:00:00Z", "UTC")).toBe("2026-10-16");
    expect(localDate("2026-10-16T20:00:00Z", "Not/AZone")).toBe("2026-10-16");
  });
});

describe("labels", () => {
  it("formats dates", () => {
    expect(shortDate("2026-09-19")).toBe("19 SEP");
    expect(longDate("2026-10-16")).toBe("Fri 16 Oct");
  });
});

describe("repoBars", () => {
  it("scales to the busiest repo", () => {
    expect(repoBars(insights.stats.ranked_repos).map((r) => r.pct)).toEqual([100, 74, 35]);
    expect(repoBars([])).toEqual([]);
  });
});

describe("scoreRows", () => {
  it("sorts by score, ties in roster order, top first", () => {
    const rows = scoreRows(insights.archetype_scores);
    expect(rows.map((r) => `${r.archetype} ${r.score}`)).toEqual([
      "MONK 52", "ROGUE 47", "PALADIN 44", "BARBARIAN 44", "WIZARD 33", "ARTIFICER 33", "BARD 10", "CLERIC 0",
    ]);
    expect(rows.filter((r) => r.top).map((r) => r.archetype)).toEqual(["MONK"]);
  });
});

import { describe, expect, it } from "vitest";
import { formatClock, formatCountdown, formatWait } from "./format";

describe("formatCountdown", () => {
  it("formats m:ss and rounds up", () => {
    expect(formatCountdown(451)).toBe("7:31");
    expect(formatCountdown(59.2)).toBe("1:00");
    expect(formatCountdown(5)).toBe("0:05");
    expect(formatCountdown(-3)).toBe("0:00");
  });
});

describe("formatClock", () => {
  it("shows the time in the hero's zone", () => {
    expect(formatClock("2026-10-16T12:32:00Z", "Asia/Kolkata")).toBe("18:02");
    expect(formatClock("2026-10-16T12:32:00Z", "UTC")).toBe("12:32");
  });

  it("falls back on a bad zone or instant", () => {
    expect(formatClock("2026-10-16T12:32:00Z", "Not/AZone")).toMatch(/^\d\d:\d\d$/);
    expect(formatClock("nope", "UTC")).toBe("--:--");
  });
});

describe("formatWait", () => {
  it("is coarse, at least 1 minute", () => {
    expect(formatWait(0)).toBe("1M");
    expect(formatWait(12 * 60_000)).toBe("12M");
    expect(formatWait((3 * 60 + 5) * 60_000)).toBe("3H 05M");
    expect(formatWait((4 * 1440 + 20 * 60) * 60_000)).toBe("4D 20H");
  });
});

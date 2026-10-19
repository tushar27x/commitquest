import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import type { Preferences } from "./api/types";
import { fieldErrors, restDayLocked, samePreferences, sameTimeZone, timeZoneOptions, toggleRestDay } from "./preferences";

const prefs: Preferences = { timezone: "Asia/Kolkata", rest_days: ["SUNDAY"], alerts_enabled: true, public_profile: false };

describe("rest-day picker", () => {
  it("adds up to 2 days, Monday first", () => {
    expect(toggleRestDay(["SUNDAY"], "WEDNESDAY")).toEqual(["WEDNESDAY", "SUNDAY"]);
  });

  it("refuses a third day and locks the others", () => {
    expect(toggleRestDay(["MONDAY", "SUNDAY"], "FRIDAY")).toEqual(["MONDAY", "SUNDAY"]);
    expect(restDayLocked(["MONDAY", "SUNDAY"], "FRIDAY")).toBe(true);
    expect(restDayLocked(["MONDAY", "SUNDAY"], "MONDAY")).toBe(false);
    expect(restDayLocked(["SUNDAY"], "FRIDAY")).toBe(false);
  });

  it("drops a picked day", () => {
    expect(toggleRestDay(["MONDAY", "SUNDAY"], "MONDAY")).toEqual(["SUNDAY"]);
  });
});

describe("samePreferences", () => {
  it("ignores rest-day order", () => {
    expect(samePreferences({ ...prefs, rest_days: ["SUNDAY", "MONDAY"] }, { ...prefs, rest_days: ["MONDAY", "SUNDAY"] })).toBe(true);
    expect(samePreferences(prefs, { ...prefs, public_profile: true })).toBe(false);
  });
});

describe("fieldErrors", () => {
  const problem = (body: unknown) => new ApiError(400, "Bad Request", null, body as never);

  it("maps errors[] by field, first message wins", () => {
    const result = fieldErrors(
      problem({
        status: 400,
        errors: [
          { field: "rest_days", message: "at most 2 rest days" },
          { field: "rest_days", message: "must not repeat a day" },
          { field: "timezone", message: "must be an IANA time zone, like Europe/Berlin" },
          { field: "mystery", message: "is odd" },
        ],
      }),
    );
    expect(result).toEqual({
      byField: { rest_days: "at most 2 rest days", timezone: "must be an IANA time zone, like Europe/Berlin" },
      other: ["mystery: is odd"],
    });
  });

  it("has a general message for a 400 without errors[]", () => {
    expect(fieldErrors(problem({ status: 400 }))?.other).toHaveLength(1);
  });

  it("is null for other statuses", () => {
    expect(fieldErrors(new ApiError(429, "Too Many Requests", 60))).toBeNull();
    expect(fieldErrors(new Error("x"))).toBeNull();
  });
});

describe("timeZoneOptions", () => {
  it("includes UTC and the given zones, sorted, once each", () => {
    const zones = timeZoneOptions("Asia/Kolkata", "Asia/Kolkata", null);
    expect(zones).toContain("UTC");
    expect(zones.filter((z) => z === "Asia/Kolkata")).toHaveLength(1);
    expect([...zones].sort()).toEqual(zones);
  });
});

describe("sameTimeZone", () => {
  it("treats an alias as the same zone", () => {
    expect(sameTimeZone("Asia/Kolkata", "Asia/Calcutta")).toBe(true);
    expect(sameTimeZone("Asia/Kolkata", "Asia/Kolkata")).toBe(true);
    expect(sameTimeZone("Asia/Kolkata", "Europe/Berlin")).toBe(false);
    expect(sameTimeZone("Asia/Kolkata", null)).toBe(false);
    expect(sameTimeZone("Nope/Zone", "Asia/Kolkata")).toBe(false);
  });
});

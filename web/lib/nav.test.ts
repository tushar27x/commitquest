import { describe, expect, it } from "vitest";
import { NAV_ICON } from "./art";
import { TABS, isActiveTab } from "./nav";

describe("nav", () => {
  it("lists the five tabs in plan order", () => {
    expect(TABS.map((t) => t.label)).toEqual(["HOME", "JOURNAL", "HERO", "ARENA", "SETTINGS"]);
  });

  it("has an icon column for every tab", () => {
    expect(TABS.map((t) => NAV_ICON[t.href])).toEqual([0, 1, 2, 3, 4]);
  });

  it("marks a tab active on its path and below it", () => {
    expect(isActiveTab("/arena", "/arena")).toBe(true);
    expect(isActiveTab("/arena/x", "/arena")).toBe(true);
    expect(isActiveTab("/arenas", "/arena")).toBe(false);
  });
});

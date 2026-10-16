import { describe, expect, it } from "vitest";
import { CLASS_ORDER, classSprite, iconSprite, pixelRuns, type IconName } from "./sprites";

describe("sprite kit", () => {
  it("has the 8 classes in roster order", () => {
    expect(CLASS_ORDER).toEqual(["PALADIN", "BARBARIAN", "WIZARD", "ARTIFICER", "CLERIC", "BARD", "MONK", "ROGUE"]);
  });

  it.each(CLASS_ORDER)("%s is 16×16 and every key has a colour", (archetype) => {
    const { grid, palette } = classSprite(archetype);
    expect(grid).toHaveLength(16);
    for (const row of grid) {
      expect(row).toHaveLength(16);
      for (const key of row) if (key !== ".") expect(palette[key], `key ${key}`).toMatch(/^#[0-9A-Fa-f]{6}$/);
    }
  });

  it.each<IconName>(["heart", "heartEmpty", "tree", "sparkle", "sweat"])("icon %s has a colour for every key", (name) => {
    const { grid, palette } = iconSprite(name);
    for (const row of grid) for (const key of row) if (key !== ".") expect(palette[key]).toBeDefined();
  });

  it.each(CLASS_ORDER)("%s stays 16×16 when sleeping and only changes one row", (archetype) => {
    const awake = classSprite(archetype).grid;
    const asleep = classSprite(archetype, { sleeping: true }).grid;
    expect(asleep).toHaveLength(16);
    for (const row of asleep) expect(row).toHaveLength(16);
    expect(asleep.filter((row, y) => row !== awake[y]).length).toBeLessThanOrEqual(1);
  });

  it("closes the monk's eyes when sleeping", () => {
    expect(classSprite("MONK", { sleeping: true }).grid[4]).toBe("....kkksskkk....");
    expect(classSprite("MONK").grid[4]).not.toBe("....kkksskkk....");
  });
});

describe("pixelRuns", () => {
  it("merges same-colour pixels per row and skips transparent ones", () => {
    const runs = pixelRuns({ grid: ["aa.b", "...."], palette: { a: "#111111", b: "#222222" } });
    expect(runs).toEqual([
      { x: 0, y: 0, width: 2, fill: "#111111" },
      { x: 3, y: 0, width: 1, fill: "#222222" },
    ]);
  });

  it("covers exactly the opaque pixels of every class", () => {
    for (const archetype of CLASS_ORDER) {
      const sprite = classSprite(archetype);
      const opaque = sprite.grid.join("").replace(/\./g, "").length;
      expect(pixelRuns(sprite).reduce((n, r) => n + r.width, 0)).toBe(opaque);
    }
  });
});

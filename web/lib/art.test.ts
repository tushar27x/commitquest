import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import type { Mood, Tier } from "./api/types";
import { CLASS_ORDER, NAV_ICON, TITLE_SCENE, UI_ART, heroSheet, portraitIndex, sceneUrl } from "./art";

const PUBLIC = join(__dirname, "..", "public");
const MOODS: Mood[] = ["OKAY", "HAPPY", "WEAK", "HUNGRY", "SLEEPING", "LEVEL_UP", "FAINTED"];
const TIERS: Tier[] = ["CAMPFIRE", "VILLAGE", "KEEP", "CITADEL"];

/** Width and height from a PNG's IHDR chunk. */
function pngSize(url: string): [number, number] {
  const png = readFileSync(join(PUBLIC, url));
  return [png.readUInt32BE(16), png.readUInt32BE(20)];
}

describe("art", () => {
  it("has the 8 classes in roster order", () => {
    expect(CLASS_ORDER).toEqual(["PALADIN", "BARBARIAN", "WIZARD", "ARTIFICER", "CLERIC", "BARD", "MONK", "ROGUE"]);
    expect(portraitIndex("PALADIN")).toBe(0);
    expect(portraitIndex("ROGUE")).toBe(7);
  });

  it.each(CLASS_ORDER)("%s has a sheet for every mood, 60 frames of 96×128 per row", (archetype) => {
    for (const mood of MOODS) {
      const { url, row, rows } = heroSheet(archetype, mood);
      expect(existsSync(join(PUBLIC, url)), url).toBe(true);
      expect(pngSize(url)).toEqual([60 * 96, rows * 128]);
      expect(row).toBeGreaterThanOrEqual(0);
      expect(row).toBeLessThan(rows);
    }
  });

  it("uses the 6-row mood sheets in the order HAPPY, WEAK, HUNGRY, SLEEPING, LEVEL UP, FAINTED", () => {
    expect(heroSheet("WIZARD", "HAPPY")).toEqual({ url: "/art/heroes/moods/wizard.png", row: 0, rows: 6 });
    expect(heroSheet("WIZARD", "FAINTED").row).toBe(5);
    expect(heroSheet("WIZARD")).toEqual({ url: "/art/heroes/idle/wizard.png", row: 0, rows: 1 });
  });

  it("gives the Monk its own sheet per mood, without the drawn-in bubble", () => {
    expect(heroSheet("MONK", "HUNGRY")).toEqual({ url: "/art/heroes/moods/monk-hungry.png", row: 0, rows: 1 });
    expect(heroSheet("MONK", "LEVEL_UP").url).toBe("/art/heroes/moods/monk-levelup.png");
  });

  it.each(TIERS)("%s has a phone and a desktop backdrop", (tier) => {
    expect(pngSize(sceneUrl(tier, "phone"))).toEqual([390, 292]);
    expect(pngSize(sceneUrl(tier, "desktop"))).toEqual([380, 260]);
  });

  it("has the title scene and the UI sheets at their documented sizes", () => {
    expect(pngSize(TITLE_SCENE.url)).toEqual([TITLE_SCENE.width, TITLE_SCENE.height]);
    expect(pngSize(UI_ART.hearts)).toEqual([312, 22]);
    expect(pngSize(UI_ART.navIcons)).toEqual([120, 48]);
    expect(pngSize(UI_ART.portraits)).toEqual([320, 48]);
    expect(pngSize(UI_ART.campfire)).toEqual([256, 48]);
    expect(Object.values(NAV_ICON).sort()).toEqual([0, 1, 2, 3, 4]);
  });
});

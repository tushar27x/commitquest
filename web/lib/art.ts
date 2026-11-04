// The hi-res art (docs/design/README.md, "Art"). The PNGs are copied from docs/design/art/ to
// public/art/ by scripts/sync-design.mjs; this module only knows their paths and frame layout.

import type { Archetype, Mood, Tier } from "./api/types";
import type { Tab } from "./nav";

/** Roster order: also the tie-break order for class scores and the order of `ui/portraits.png`. */
export const CLASS_ORDER: readonly Archetype[] = ["PALADIN", "BARBARIAN", "WIZARD", "ARTIFICER", "CLERIC", "BARD", "MONK", "ROGUE"];

/** The class accent from the theme (`--color-class-*`, generated from tokens.json). */
export function classAccent(archetype: Archetype): string {
  return `var(--color-class-${archetype.toLowerCase()})`;
}

/** The class accent mixed into transparent, for glows behind a sprite. */
export function classGlow(archetype: Archetype, percent: number): string {
  return `color-mix(in srgb, ${classAccent(archetype)} ${percent}%, transparent)`;
}

const ART = "/art";

// Hero sheets: 60 frames of 96×128 per row. Idle (OKAY) has one row; the other moods share a
// 6-row sheet per class, except the Monk, who has one sheet per mood.
export const FRAME_W = 96;
export const FRAME_H = 128;
export const FRAMES = 60;

const MOOD_ROWS: readonly Mood[] = ["HAPPY", "WEAK", "HUNGRY", "SLEEPING", "LEVEL_UP", "FAINTED"];
const MONK_FILES: Record<Exclude<Mood, "OKAY">, string> = {
  HAPPY: "happy",
  WEAK: "weak",
  // Not `hungry-bubble`: Home draws the speech bubble in HTML.
  HUNGRY: "hungry",
  SLEEPING: "sleeping",
  LEVEL_UP: "levelup",
  FAINTED: "fainted",
};

export interface HeroSheet {
  url: string;
  /** Row of the mood in the sheet, from 0. */
  row: number;
  /** Rows in the sheet. */
  rows: number;
}

export function heroSheet(archetype: Archetype, mood: Mood = "OKAY"): HeroSheet {
  const name = archetype.toLowerCase();
  if (mood === "OKAY") return { url: `${ART}/heroes/idle/${name}.png`, row: 0, rows: 1 };
  if (archetype === "MONK") return { url: `${ART}/heroes/moods/monk-${MONK_FILES[mood]}.png`, row: 0, rows: 1 };
  return { url: `${ART}/heroes/moods/${name}.png`, row: MOOD_ROWS.indexOf(mood), rows: MOOD_ROWS.length };
}

/** Home backdrops: phone 390×292 drawn at ×1, desktop 380×260 drawn at ×2. */
export const SCENE = {
  phone: { width: 390, height: 292, scale: 1 },
  desktop: { width: 380, height: 260, scale: 2 },
} as const;

export function sceneUrl(tier: Tier, size: keyof typeof SCENE): string {
  return `${ART}/scenes/${tier.toLowerCase()}-${size}.png`;
}

export const TITLE_SCENE = { url: `${ART}/scenes/title.png`, width: 390, height: 600 } as const;

export const UI_ART = {
  hearts: `${ART}/ui/hearts.png`,
  navIcons: `${ART}/ui/nav-icons.png`,
  portraits: `${ART}/ui/portraits.png`,
  campfire: `${ART}/ui/campfire.png`,
} as const;

/** Column of a tab's icon in `ui/nav-icons.png` (row 0 idle, row 1 active). Column 3, the Arena's, is unused. */
export const NAV_ICON: Record<Tab["href"], number> = { "/home": 0, "/journal": 1, "/hero": 2, "/settings": 4 };

/** Column of a class in `ui/portraits.png`. */
export function portraitIndex(archetype: Archetype): number {
  return CLASS_ORDER.indexOf(archetype);
}

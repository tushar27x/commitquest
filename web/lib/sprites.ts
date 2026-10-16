// Typed access to the sprite kit. `design/sprites.json` is a copy of docs/design/sprites.json
// made by scripts/sync-design.mjs: never edit grids or colours by hand.

import type { Archetype } from "./api/types.tmp";
import kit from "./design/sprites.json";

export type Palette = Readonly<Record<string, string>>;

export interface Sprite {
  grid: readonly string[];
  palette: Palette;
}

export type IconName = "heart" | "heartEmpty" | "tree" | "sparkle" | "sweat";

/** Roster order: also the tie-break order for class scores. */
export const CLASS_ORDER = Object.keys(kit.classes) as Archetype[];

export function classAccent(archetype: Archetype): string {
  return kit.classes[archetype].accent;
}

/** A class sprite on the shared palette. `sleeping` closes the eyes where the kit has a variant. */
export function classSprite(archetype: Archetype, { sleeping = false } = {}): Sprite {
  const { grid, palette } = kit.classes[archetype];
  const eyes: string | undefined = (kit.variants.sleepingEyesRow as Record<string, string>)[archetype];
  return {
    grid: sleeping && eyes ? grid.map((row, y) => (y === 4 ? eyes : row)) : grid,
    palette: { ...kit.basePalette, ...palette },
  };
}

export function iconSprite(name: IconName): Sprite {
  const { icons } = kit;
  switch (name) {
    case "heart":
      return { grid: icons.heart.grid, palette: icons.heart.full };
    case "heartEmpty":
      return { grid: icons.heart.grid, palette: icons.heart.empty };
    default:
      return { grid: icons[name].grid, palette: icons[name].palette };
  }
}

/** Horizontal runs of one colour per row, so a sprite is a handful of rects, not 256. */
export interface PixelRun {
  x: number;
  y: number;
  width: number;
  fill: string;
}

export function pixelRuns({ grid, palette }: Sprite): PixelRun[] {
  const runs: PixelRun[] = [];
  grid.forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      const key = row[x];
      let end = x + 1;
      while (end < row.length && row[end] === key) end++;
      const fill = key === "." ? undefined : palette[key];
      if (fill) runs.push({ x, y, width: end - x, fill });
      x = end;
    }
  });
  return runs;
}

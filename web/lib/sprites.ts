// Typed access to the sprite kit. `design/sprites.json` is a copy of docs/design/sprites.json
// made by scripts/sync-design.mjs: never edit grids or colours by hand.

import type { Archetype } from "./api/types";
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

interface EyesRow {
  row: number;
  value: string;
}

/**
 * The closed-eyes row for a class, or undefined when the class has none (the Paladin's
 * visor hides its eyes). Reads `variants.sleepingEyes` ({CLASS: {row, value}}, PR #19);
 * until the kit has it, falls back to the deprecated `sleepingEyesRow` (MONK, row 4).
 * Drop the fallback once integration removes the old key.
 */
function sleepingEyes(archetype: Archetype): EyesRow | undefined {
  // Through `unknown`: the kit's shape changes between versions, and `_doc` sits next to the rows.
  const variants = kit.variants as unknown as { sleepingEyes?: Record<string, unknown>; sleepingEyesRow?: Record<string, unknown> };
  if (variants.sleepingEyes) {
    const eyes = variants.sleepingEyes[archetype];
    return eyes && typeof eyes === "object" ? (eyes as EyesRow) : undefined;
  }
  const legacy = variants.sleepingEyesRow?.[archetype];
  return typeof legacy === "string" ? { row: 4, value: legacy } : undefined;
}

/** A class sprite on the shared palette. `sleeping` closes the eyes where the kit has a variant. */
export function classSprite(archetype: Archetype, { sleeping = false } = {}): Sprite {
  const { grid, palette } = kit.classes[archetype];
  const eyes = sleeping ? sleepingEyes(archetype) : undefined;
  return {
    grid: eyes ? grid.map((row, y) => (y === eyes.row ? eyes.value : row)) : grid,
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

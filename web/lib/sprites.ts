// Tiny one-colour icons drawn as grids (the "QUEST DONE" tick). The heroes, hearts, nav icons and
// portraits are PNG art now: see lib/art.ts.

export type Palette = Readonly<Record<string, string>>;

export interface Sprite {
  grid: readonly string[];
  palette: Palette;
}

/** Horizontal runs of one colour per row, so a sprite is a handful of rects, not one per pixel. */
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

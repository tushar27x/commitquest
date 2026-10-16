import type { CSSProperties } from "react";
import { pixelRuns, type Palette } from "@/lib/sprites";

export interface PixelSpriteProps {
  /** Rows of palette keys; `.` is transparent. */
  grid: readonly string[];
  palette: Palette;
  /** Screen pixels per sprite pixel. Integers only (×2 lists, ×3–4 small, ×6–10 hero). */
  scale?: number;
  /** Accessible name. Without one the sprite is decorative and hidden from assistive tech. */
  label?: string;
  className?: string;
  style?: CSSProperties;
}

/** Renders a sprite grid as crisp SVG rects, one per same-colour run. */
export function PixelSprite({ grid, palette, scale = 4, label, className, style }: PixelSpriteProps) {
  const px = Math.max(1, Math.round(scale));
  const width = Math.max(0, ...grid.map((row) => row.length));
  const height = grid.length;
  const a11y = label ? { role: "img", "aria-label": label } : { "aria-hidden": true };

  return (
    <svg
      {...a11y}
      width={width * px}
      height={height * px}
      viewBox={`0 0 ${width} ${height}`}
      shapeRendering="crispEdges"
      className={className}
      style={{ display: "block", imageRendering: "pixelated", ...style }}
    >
      {pixelRuns({ grid, palette }).map((run) => (
        <rect key={`${run.x},${run.y}`} x={run.x} y={run.y} width={run.width} height={1} fill={run.fill} />
      ))}
    </svg>
  );
}

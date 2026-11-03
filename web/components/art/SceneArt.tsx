import type { CSSProperties, ReactNode } from "react";
import { UI_ART } from "@/lib/art";
import type { SceneEdge } from "@/lib/scene-edges";

/** The campfire's 8-frame flame (`ui/campfire.png`) with its flickering glow. Positions in frame pixels. */
export function Campfire({ scale, fire, glow }: { scale: 1 | 2; fire: [number, number]; glow: [number, number, number, number] }) {
  const [gx, gy, gw, gh] = glow;
  return (
    <>
      <div aria-hidden className="fire-glow absolute" style={{ left: gx, top: gy, width: gw, height: gh }} />
      <div
        aria-hidden
        className="art campfire absolute"
        style={{ left: fire[0], top: fire[1], backgroundImage: `url(${UI_ART.campfire})`, ["--s" as string]: scale }}
      />
    </>
  );
}

/** Twinkling stars: [x, y, delay in s]. */
export function Stars({ stars, size = 3 }: { stars: readonly (readonly [number, number, number])[]; size?: number }) {
  return stars.map(([x, y, delay]) => (
    <span
      key={`${x},${y}`}
      aria-hidden
      className="star"
      style={{ left: x, top: y, width: size, height: size, animationDelay: `${-delay}s` } as CSSProperties}
    />
  ));
}

/** One side's edge colours as a CSS gradient: hard bands for the ground, soft stops above it. */
export function edgeGradient({ ground, above }: SceneEdge, scale: number): string {
  let from = 0;
  const bands = ground.map(([colour, to]) => {
    const band = `${colour} ${from * scale}px ${to * scale}px`;
    from = to;
    return band;
  });
  const soft = above.map(([colour, row]) => `${colour} ${row * scale}px`);
  return `linear-gradient(to top, ${[...bands, ...soft].join(", ")})`;
}

/**
 * A pixel-art picture at an integer scale, centred and anchored to the bottom of its parent. With
 * `edges`, a wider parent continues the picture past each side with its edge colours (lib/scene-edges.ts), so the scene never repeats. Children are positioned in the
 * picture's own pixels (scaled).
 */
export function PixelScene({
  url,
  width,
  height,
  scale,
  edges,
  className = "",
  children,
}: {
  url: string;
  width: number;
  height: number;
  scale: number;
  edges?: { left: SceneEdge; right: SceneEdge };
  className?: string;
  children?: ReactNode;
}) {
  const w = width * scale;
  const h = height * scale;
  // Wide enough for any screen: the parent clips it.
  const side = (bands: SceneEdge, at: "right" | "left"): CSSProperties => ({
    position: "absolute",
    bottom: 0,
    [at]: "100%",
    width: "100vw",
    height: h,
    backgroundImage: edgeGradient(bands, scale),
  });
  return (
    <div className={`absolute bottom-0 left-1/2 ${className}`} style={{ width: w, height: h, marginLeft: -w / 2 }}>
      {edges && <div aria-hidden style={side(edges.left, "right")} />}
      {edges && <div aria-hidden style={side(edges.right, "left")} />}
      <div
        className="art absolute bottom-0 left-0"
        style={{ width: w, height: h, backgroundImage: `url(${url})`, backgroundSize: `${w}px ${h}px` }}
      >
        {children}
      </div>
    </div>
  );
}

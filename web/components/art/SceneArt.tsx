import type { CSSProperties, ReactNode } from "react";
import { UI_ART } from "@/lib/art";

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

/**
 * A pixel-art picture at an integer scale, centred and anchored to the bottom of its parent. With
 * `mirrored`, flipped copies either side continue it, so a wider parent never shows a hard edge
 * (landscapes only: a castle or a moon would repeat). Children are positioned in the picture's
 * own pixels (scaled).
 */
export function PixelScene({
  url,
  width,
  height,
  scale,
  mirrored = true,
  className = "",
  children,
}: {
  url: string;
  width: number;
  height: number;
  scale: number;
  mirrored?: boolean;
  className?: string;
  children?: ReactNode;
}) {
  const w = width * scale;
  const h = height * scale;
  const tile = (x: number, mirrored: boolean): CSSProperties => ({
    position: "absolute",
    left: x,
    bottom: 0,
    width: w,
    height: h,
    backgroundImage: `url(${url})`,
    backgroundSize: `${w}px ${h}px`,
    transform: mirrored ? "scaleX(-1)" : undefined,
  });
  return (
    <div className={`absolute bottom-0 left-1/2 ${className}`} style={{ width: w, height: h, marginLeft: -w / 2 }}>
      {mirrored && <div aria-hidden className="art" style={tile(-w, true)} />}
      {mirrored && <div aria-hidden className="art" style={tile(w, true)} />}
      <div className="art" style={tile(0, false)}>
        {children}
      </div>
    </div>
  );
}

import type { CSSProperties } from "react";
import type { Archetype, Mood } from "@/lib/api/types";
import { heroSheet } from "@/lib/art";

export interface HeroSpriteProps {
  archetype: Archetype;
  /** OKAY is the idle loop; every other mood has its own loop. */
  mood?: Mood;
  /** Integer scale: ×1 (96×128), ×2 (192×256) or ×3 (288×384). */
  scale?: 1 | 2 | 3;
  /** Accessible name. Without one the sprite is decorative and hidden from assistive tech. */
  label?: string;
  className?: string;
  style?: CSSProperties;
}

/** An animated hero from the PNG sprite sheets (docs/design/README.md "Art"). */
export function HeroSprite({ archetype, mood = "OKAY", scale = 1, label, className = "", style }: HeroSpriteProps) {
  const { url, row, rows } = heroSheet(archetype, mood);
  const a11y = label ? { role: "img", "aria-label": label } : { "aria-hidden": true };
  return (
    <div
      {...a11y}
      className={`art hero-sprite ${className}`}
      style={{
        backgroundImage: `url(${url})`,
        ["--s" as string]: scale,
        ["--row" as string]: row,
        ["--rows" as string]: rows,
        ...style,
      }}
    />
  );
}

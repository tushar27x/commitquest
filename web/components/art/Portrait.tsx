import type { CSSProperties } from "react";
import type { Archetype } from "@/lib/api/types";
import { UI_ART, portraitIndex } from "@/lib/art";

/** A 40×48 class portrait (`ui/portraits.png`). Decorative: the class name is always next to it. */
export function Portrait({ archetype, dimmed = false, className = "", style }: { archetype: Archetype; dimmed?: boolean; className?: string; style?: CSSProperties }) {
  return (
    <span
      aria-hidden
      className={`art portrait ${className}`}
      style={{
        backgroundImage: `url(${UI_ART.portraits})`,
        backgroundPosition: `${-40 * portraitIndex(archetype)}px 0`,
        filter: dimmed ? "grayscale(1) brightness(0.7)" : undefined,
        ...style,
      }}
    />
  );
}

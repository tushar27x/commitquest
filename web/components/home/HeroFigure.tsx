import { HeroSprite } from "@/components/art/HeroSprite";
import type { Archetype, Mood } from "@/lib/api/types";

// The hero on Home with its mood (docs/design/source/HomeMobile, HomeDesktop, Moods). Every mood is
// its own animated loop in the sprite sheets; only the speech bubble, the level label and the
// level-up rays are drawn here. Sizes are the boards' pixels at ×1 (phone) and ×2 (desktop).

export interface HeroFigureProps {
  archetype: Archetype;
  mood: Mood;
  scale: 1 | 2;
  /** "LV 8!" over LEVEL_UP, "LV 7 → 6" over FAINTED. */
  levelLabel: string;
  /** Speech bubble text when HUNGRY. */
  hungryText: string;
  /** Moves the bubble sideways, so it stays over the middle of the scene when the hero doesn't. */
  bubbleShift?: number;
}

// Offsets from the sprite frame, per scale (the boards don't scale these linearly).
const RAYS = { 1: { size: 190, drop: 7 }, 2: { size: 300, drop: 38 } } as const;
const BUBBLE = { 1: { left: 78, bottom: 130 }, 2: { left: 136, bottom: 218 } } as const;
/** The level label sits just above the frame, clear of the head. */
const LABEL_TOP = { 1: -18, 2: -10 } as const;

/** A 96×128 × scale box, anchored by its parent; the extras overflow it. */
export function HeroFigure({ archetype, mood, scale, levelLabel, hungryText, bubbleShift = 0 }: HeroFigureProps) {
  const w = 96 * scale;
  const h = 128 * scale;
  const rays = RAYS[scale];
  const bubble = BUBBLE[scale];

  return (
    <div className="relative" style={{ width: w, height: h }}>
      {mood === "LEVEL_UP" && (
        <div
          aria-hidden
          className="level-rays absolute"
          style={{ left: (w - rays.size) / 2, top: (h - rays.size) / 2 + rays.drop, width: rays.size, height: rays.size }}
        />
      )}

      {mood === "FAINTED" ? (
        <>
          {/* Lying down: the fainted loop turned on its side, on a ground shadow. */}
          <div
            aria-hidden
            className="absolute"
            style={{
              left: -11 * scale,
              bottom: 2 * scale,
              width: 118 * scale,
              height: 7 * scale,
              background: "radial-gradient(ellipse, rgba(5, 4, 10, 0.7) 0 50%, transparent 72%)",
            }}
          />
          <div className="absolute" style={{ left: -16 * scale, bottom: 2 * scale, width: 128 * scale, height: 96 * scale }}>
            <HeroSprite archetype={archetype} mood="FAINTED" scale={scale} className="absolute rotate-90" style={{ left: 16 * scale, top: -16 * scale }} />
          </div>
        </>
      ) : (
        <HeroSprite archetype={archetype} mood={mood} scale={scale} className="relative" />
      )}

      {mood === "HUNGRY" && (
        <div
          className="absolute flex items-center gap-1.5 bg-bone px-[9px] py-1.5 whitespace-nowrap text-ink shadow-btn md:gap-2.5 md:px-3 md:py-2.5"
          style={{ left: bubble.left + bubbleShift, bottom: bubble.bottom }}
        >
          <span aria-hidden className="font-display text-[11px] text-ember-glyph md:text-[14px]">
            !
          </span>
          <span className="text-[18px] leading-none md:text-[20px]">{hungryText}</span>
        </div>
      )}

      {(mood === "LEVEL_UP" || mood === "FAINTED") && (
        <span
          className={`absolute inset-x-[-100%] text-center font-display text-[11px] whitespace-nowrap [text-shadow:3px_3px_0_var(--color-outline)] md:text-[13px] ${
            mood === "LEVEL_UP" ? "text-gold" : "text-ember"
          }`}
          style={{ top: LABEL_TOP[scale] }}
        >
          {levelLabel}
        </span>
      )}
    </div>
  );
}

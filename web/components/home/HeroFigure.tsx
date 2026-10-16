import type { CSSProperties } from "react";
import { PixelSprite } from "@/components/PixelSprite";
import type { Archetype, BaseMood, Mood } from "@/lib/api/types";
import { classSprite, iconSprite } from "@/lib/sprites";

// The hero sprite with its mood overlays (plan §5, docs/design/source/character-Moods.dc.html).
// Overlay positions are fractions of the sprite size S, taken from the 96 px board sprite.

export interface HeroFigureProps {
  archetype: Archetype;
  mood: Mood;
  baseMood: BaseMood;
  scale: number;
  /** "LV 8!" over LEVEL_UP, "LV 7 → 6" over FAINTED. */
  levelLabel: string;
  /** Speech bubble text when HUNGRY. */
  hungryText: string;
}

const SPARKLE = iconSprite("sparkle");
const SWEAT = iconSprite("sweat");

export function HeroFigure({ archetype, mood, baseMood, scale, levelLabel, hungryText }: HeroFigureProps) {
  const S = 16 * scale;
  const at = (x: number, y: number): CSSProperties => ({ position: "absolute", left: x * S, top: y * S });

  const oneOff = mood === "LEVEL_UP" || mood === "FAINTED";
  const sleeping = mood === "SLEEPING";
  const weak = !oneOff && baseMood === "WEAK";
  const sparkles = !oneOff && !sleeping && baseMood === "HAPPY";
  const still = sleeping || mood === "FAINTED";

  const filter = mood === "FAINTED" ? "saturate(0.6)" : weak ? "saturate(0.45) brightness(0.85)" : undefined;
  const transform = mood === "FAINTED" ? `rotate(90deg) translateX(${0.31 * S}px)` : undefined;

  return (
    <div className="relative" style={{ width: S, height: S }}>
      {mood === "LEVEL_UP" && (
        <div
          aria-hidden
          className="anim-turn absolute"
          style={{
            left: -0.125 * S,
            top: -0.35 * S,
            width: 1.25 * S,
            height: 1.25 * S,
            background: "repeating-conic-gradient(rgba(242, 182, 50, 0.35) 0 15deg, transparent 15deg 30deg)",
          }}
        />
      )}

      {/* Ground shadow */}
      <div aria-hidden className="absolute bg-outline/40" style={{ left: 0.125 * S, top: S, width: 0.75 * S, height: Math.round(scale * 1.33) }} />

      <div className={still ? "relative" : "anim-bob relative"} style={{ ["--bob" as string]: `${-scale}px`, transform, filter }}>
        <PixelSprite {...classSprite(archetype, { sleeping })} scale={scale} />
        {weak && (
          <div
            aria-hidden
            className="absolute bg-bone"
            style={{ left: 0.31 * S, top: 0.083 * S, width: 0.375 * S, height: 0.083 * S, boxShadow: "0 0 0 2px var(--color-ink)", rotate: "-14deg" }}
          />
        )}
      </div>

      {weak && (
        <div aria-hidden className="anim-twinkle" style={{ ...at(0.885, -0.02), animationDuration: "1.4s" }}>
          <PixelSprite {...SWEAT} scale={Math.round((scale * 2) / 3)} />
        </div>
      )}

      {sparkles && (
        <>
          <div aria-hidden className="anim-twinkle" style={at(-0.18, -0.4)}>
            <PixelSprite {...SPARKLE} scale={Math.max(2, Math.round(scale / 2))} />
          </div>
          <div aria-hidden className="anim-twinkle" style={{ ...at(1.05, -0.08), animationDelay: "0.45s" }}>
            <PixelSprite {...SPARKLE} scale={Math.max(2, Math.round(scale / 2))} />
          </div>
        </>
      )}

      {mood === "HUNGRY" && (
        <div
          className="absolute flex items-center gap-1.5 bg-bone px-2 py-1.5 whitespace-nowrap text-ink shadow-btn-flat md:gap-2.5 md:px-3 md:py-2.5"
          style={{ left: 0.89 * S, bottom: 0.94 * S }}
        >
          <span aria-hidden className="font-display text-[11px] text-ember-glyph md:text-[14px]">
            !
          </span>
          <span className="text-[20px] leading-none">{hungryText}</span>
        </div>
      )}

      {sleeping && (
        <span
          aria-hidden
          className="absolute font-display text-[12px] leading-[1.2] whitespace-pre text-muted md:text-[14px]"
          style={at(0.95, -0.45)}
        >
          {"Z\n  z"}
        </span>
      )}

      {mood === "FAINTED" && (
        <span
          aria-hidden
          className="anim-twinkle absolute inset-x-[-50%] text-center font-display text-[12px] tracking-[10px] text-gold"
          style={{ top: 0.19 * S }}
        >
          + * +
        </span>
      )}

      {oneOff && (
        <span
          className={`absolute inset-x-[-100%] text-center font-display text-[11px] whitespace-nowrap [text-shadow:2px_2px_0_var(--color-outline)] md:text-[14px] ${
            mood === "LEVEL_UP" ? "text-gold" : "text-ember"
          }`}
          style={{ top: -0.62 * S }}
        >
          {levelLabel}
        </span>
      )}
    </div>
  );
}

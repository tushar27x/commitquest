import { HeartRow } from "@/components/HeartRow";
import type { Hero, HeroEvent, Tier } from "@/lib/api/types";
import { TIER_LEVELS, faintLabel } from "@/lib/hero";
import { HeroFigure, type HeroFigureProps } from "./HeroFigure";
import { SceneStage } from "./SceneStage";
import "@/app/scenes.css";

const MOOD_WORDS: Record<Hero["mood"], string> = {
  HAPPY: "happy",
  OKAY: "okay",
  WEAK: "weak",
  HUNGRY: "rusty, today's quest still open",
  SLEEPING: "sleeping, it's a rest day",
  LEVEL_UP: "celebrating a level up",
  FAINTED: "fainted, hearts refilled",
};

const BADGE = "absolute bg-night/78 shadow-btn-flat";

function Backdrop({ tier, figure }: { tier: Tier; figure: Omit<HeroFigureProps, "scale" | "hungryText"> }) {
  return (
    <SceneStage tier={tier}>
      <HeroFigure {...figure} scale={2} hungryText="We're getting rusty. No code today." />
    </SceneStage>
  );
}

/** The pet: tier backdrop, the hero with its mood, hearts and streak (phone 292 px, desktop 520 px). */
export function HeroScene({ hero, events, className = "" }: { hero: Hero; events: HeroEvent[] | undefined; className?: string }) {
  const cls = hero.class.current;
  const name = cls.charAt(0) + cls.slice(1).toLowerCase();
  const levelLabel = hero.mood === "FAINTED" ? faintLabel(hero, events) : `LV ${hero.level}!`;
  const figure = { archetype: cls, mood: hero.mood, levelLabel };

  return (
    <section aria-label="Your hero" className={`relative h-[292px] min-w-0 overflow-hidden bg-[#1a1636] md:h-[520px] md:shadow-px-drop ${className}`}>
      <Backdrop tier={hero.tier} figure={figure} />
      <p className="sr-only">
        {name}, level {hero.level}, {hero.lives} of {hero.max_lives} hearts, streak {hero.streak}, {MOOD_WORDS[hero.mood]}.
      </p>

      <div aria-hidden className={`${BADGE} top-3 left-3 flex items-center gap-2.5 px-2 py-1.5 md:top-5 md:left-5 md:px-3 md:py-2.5`}>
        <HeartRow lives={hero.lives} max={hero.max_lives} className="gap-1 md:gap-1.5" />
        <span className="hidden font-display text-[10px] md:inline">
          {hero.lives}/{hero.max_lives}
        </span>
      </div>
      <div aria-hidden className={`${BADGE} top-3 right-3 px-2.5 py-[9px] font-display text-[8px] md:top-5 md:right-5 md:px-3 md:py-2.5 md:text-[10px]`}>
        STREAK <span className="text-gold">{hero.streak}</span>
      </div>
      <span className="absolute bottom-3.5 left-5 hidden bg-[#e3c88f] px-2 py-1.5 font-display text-[8px] text-night md:block">
        {hero.tier} · {TIER_LEVELS[hero.tier]}
      </span>
    </section>
  );
}

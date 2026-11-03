import { HeartRow } from "@/components/HeartRow";
import { Campfire, PixelScene, Stars } from "@/components/art/SceneArt";
import type { Hero, HeroEvent, Tier } from "@/lib/api/types";
import { SCENE, sceneUrl } from "@/lib/art";
import { SCENE_EDGES } from "@/lib/scene-edges";
import { TIER_LEVELS, faintLabel } from "@/lib/hero";
import { HeroFigure, type HeroFigureProps } from "./HeroFigure";

const MOOD_WORDS: Record<Hero["mood"], string> = {
  HAPPY: "happy",
  OKAY: "okay",
  WEAK: "weak",
  HUNGRY: "rusty, today's quest still open",
  SLEEPING: "sleeping, it's a rest day",
  LEVEL_UP: "celebrating a level up",
  FAINTED: "fainted, hearts refilled",
};

// Where things stand in each backdrop, in the art's pixels at its drawn scale
// (docs/design/source/HomeMobile, HomeDesktop, TierHome; U8 backdrops). The campfire hero stands
// left of the fire, which sits on the logs drawn in the art.
const LAYOUT = {
  phone: {
    centre: 147,
    hero: (tier: Tier) => ({ left: tier === "CAMPFIRE" ? 176 : 147, bottom: 20 }),
    fire: { fire: [304, 216], glow: [230, 167, 190, 150] },
    stars: [[40, 18, 0], [188, 34, 1.2], [310, 14, 2.1], [120, 52, 0.6]],
    starSize: 3,
  },
  desktop: {
    centre: 284,
    hero: (tier: Tier) => ({ left: tier === "CAMPFIRE" ? 330 : 284, bottom: 34 }),
    fire: { fire: [591, 376], glow: [470, 308, 330, 260] },
    stars: [[90, 40, 0], [380, 70, 1.1], [620, 30, 2], [240, 110, 0.5]],
    starSize: 4,
  },
} as const;

/** Night tiers twinkle. */
const STARRY: Record<Tier, boolean> = { CAMPFIRE: true, VILLAGE: true, KEEP: false, CITADEL: false };

const BADGE = "absolute bg-night/78 shadow-btn-flat";

function Backdrop({ tier, size, figure }: { tier: Tier; size: "phone" | "desktop"; figure: Omit<HeroFigureProps, "scale" | "hungryText"> }) {
  const { width, height, scale } = SCENE[size];
  const layout = LAYOUT[size];
  const at = layout.hero(tier);
  return (
    <PixelScene url={sceneUrl(tier, size)} width={width} height={height} scale={scale} edges={SCENE_EDGES[tier][size]} className={size === "phone" ? "md:hidden" : "hidden md:block"}>
      {STARRY[tier] && <Stars stars={layout.stars} size={layout.starSize} />}
      {tier === "CAMPFIRE" && <Campfire scale={scale} fire={[...layout.fire.fire]} glow={[...layout.fire.glow]} />}
      <div className="absolute" style={at}>
        <HeroFigure
          {...figure}
          scale={scale}
          hungryText={size === "phone" ? "Getting rusty" : "We're getting rusty. No code today."}
          bubbleShift={layout.centre - at.left}
        />
      </div>
    </PixelScene>
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
      <div aria-hidden className="absolute inset-0">
        <Backdrop tier={hero.tier} size="phone" figure={figure} />
        <Backdrop tier={hero.tier} size="desktop" figure={figure} />
      </div>
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

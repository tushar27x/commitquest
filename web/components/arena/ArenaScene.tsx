import { HeartRow } from "@/components/HeartRow";
import { HeroFigure } from "@/components/home/HeroFigure";
import { SceneStage } from "@/components/home/SceneStage";
import type { Archetype, Hero } from "@/lib/api/types";
import { classAccent } from "@/lib/art";
import { className as classLabel } from "@/lib/journal";
import "@/app/scenes.css";

const BADGE = "absolute bg-night/78 shadow-btn-flat";

/**
 * The Arena's banner: the hero on its tier backdrop (the same animated scenes as Home, U8), with
 * the class of the board being viewed (#72). Mood, level label and hearts are the user's own, so
 * they show only on the user's own board. Phone 292 px, desktop 520 px.
 */
export function ArenaScene({ hero, archetype, className = "" }: { hero: Hero; archetype: Archetype; className?: string }) {
  const cls = archetype;
  const own = cls === hero.class.current;
  return (
    <section aria-label="Your place in the Arena" className={`relative h-[292px] min-w-0 overflow-hidden bg-[#1a1636] md:h-[520px] md:shadow-px-drop ${className}`}>
      <SceneStage tier={hero.tier}>
        <HeroFigure archetype={cls} mood={own && hero.mood !== "HUNGRY" ? hero.mood : "OKAY"} scale={2} levelLabel={`LV ${hero.level}!`} hungryText="" />
      </SceneStage>
      <p className="sr-only">
        {own ? `${classLabel(cls)}, level ${hero.level}, ${hero.lives} of ${hero.max_lives} hearts.` : `${classLabel(cls)} board.`}
      </p>
      <div aria-hidden className={`${BADGE} top-3 left-3 px-2.5 py-[9px] font-display text-[8px] md:top-5 md:left-5 md:px-3 md:py-2.5 md:text-[10px]`}>
        <span style={{ color: classAccent(cls) }}>{cls}</span> BOARD
      </div>
      {own && (
        <div aria-hidden className={`${BADGE} top-3 right-3 px-2 py-1.5 md:top-5 md:right-5 md:px-3 md:py-2.5`}>
          <HeartRow lives={hero.lives} max={hero.max_lives} className="gap-1 md:gap-1.5" />
        </div>
      )}
    </section>
  );
}

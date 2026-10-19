"use client";

import Link from "next/link";
import { PixelSprite } from "@/components/PixelSprite";
import { Arrow } from "@/components/ui/Arrow";
import type { Archetype, Hero } from "@/lib/api/types";
import { className } from "@/lib/journal";
import { useHero, usePreferences } from "@/lib/queries";
import { classAccent, classSprite } from "@/lib/sprites";
import { ArenaScene } from "./ArenaScene";

const CARD = "flex flex-col gap-2.5 bg-stone px-3.5 py-3 shadow-px md:px-5 md:py-[18px]";
const HEADING = "font-display text-[9px] leading-normal md:text-[10px]";

/**
 * Arena (plan §4): "under construction" until the leaderboards land in Phase 13 (B8).
 * Screen only renders it once the hero has loaded.
 */
export function ArenaView() {
  const hero = useHero().data;
  if (!hero) return null;

  return (
    <div className="flex flex-col gap-4 md:flex-row md:flex-wrap md:items-start md:gap-8">
      <ArenaScene className="md:flex-[999_1_560px]" />
      <div className="flex min-w-0 flex-col gap-3.5 px-3 md:flex-[1_1_340px] md:gap-6 md:px-0">
        <section aria-labelledby="arena-soon" className={CARD}>
          <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
            <h2 id="arena-soon" className={`${HEADING} text-gold`}>
              UNDER CONSTRUCTION
            </h2>
            <span className="font-display text-[7px] text-muted-2 md:text-[8px]">PHASE 13</span>
          </div>
          <p className="text-muted">
            The Arena opens when the leaderboards are built. Heroes rank against their own class: highest level first, then XP.
          </p>
        </section>
        <EntryCard hero={hero} />
        <VisibilityCard archetype={hero.class.current} />
      </div>
    </div>
  );
}

/** Which board the hero would join: the class it plays now. */
function EntryCard({ hero }: { hero: Hero }) {
  const cls = hero.class.current;
  return (
    <section aria-labelledby="arena-entry" className={CARD}>
      <h2 id="arena-entry" className={HEADING}>
        WHEN THE GATES OPEN
      </h2>
      <div className="flex items-center gap-3.5">
        <div className="shrink-0">
          <PixelSprite {...classSprite(cls)} scale={3} />
        </div>
        <p className="text-muted">
          You&apos;d enter the{" "}
          <span className="font-display text-[10px]" style={{ color: classAccent(cls) }}>
            {cls}
          </span>{" "}
          board at LV {hero.level}. Changing class moves you to that class&apos;s board.
        </p>
      </div>
    </section>
  );
}

/** "Show me in the Arena" from Settings (`public_profile`). Hidden while preferences load or fail. */
function VisibilityCard({ archetype }: { archetype: Archetype }) {
  const preferences = usePreferences().data;
  if (!preferences) return null;
  const shown = preferences.public_profile;
  return (
    <section aria-labelledby="arena-visibility" className={CARD}>
      <h2 id="arena-visibility" className={`${HEADING} ${shown ? "text-success" : "text-muted-2"}`}>
        {shown ? "YOU'LL BE ON THE BOARD" : "HIDDEN FROM THE BOARD"}
      </h2>
      <p className="text-muted">
        {shown
          ? "Your hero will appear on the public leaderboard for its class."
          : `Your hero won't appear on the public leaderboard. Turn on "Show me in the Arena" to compete on the ${className(archetype)} board.`}
      </p>
      <Link
        href="/settings"
        className="relative flex items-center gap-1.5 self-start py-2 font-display text-[8px] text-gold after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-[''] md:text-[9px]"
      >
        {shown ? "CHANGE IN SETTINGS" : "SHOW ME IN SETTINGS"} <Arrow />
      </Link>
    </section>
  );
}

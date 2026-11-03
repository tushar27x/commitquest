"use client";

import { useHero, useHeroEvents } from "@/lib/queries";
import { HeroScene } from "./HeroScene";
import { LevelPanel, RecentCard, TodayCard, WeekCard } from "./HomeCards";

/** Home (the pet). Screen only renders it once the hero has loaded. */
export function HomeView() {
  const hero = useHero().data;
  const events = useHeroEvents(30);
  if (!hero) return null;

  // One column from md up (Screen narrow: 760 px, the scene's ×2 width): the scene, then the
  // cards in rows of two (XP + today, week + recently), one per row where two don't fit.
  return (
    <div className="flex flex-col gap-4 md:gap-6">
      <HeroScene hero={hero} events={events.data?.events} />
      <div className="flex min-w-0 flex-col gap-3.5 px-3 md:grid md:grid-cols-[repeat(auto-fit,minmax(320px,1fr))] md:gap-6 md:px-0">
        <LevelPanel hero={hero} />
        <TodayCard hero={hero} />
        <WeekCard hero={hero} />
        <RecentCard today={hero.today.date} events={events} />
      </div>
    </div>
  );
}

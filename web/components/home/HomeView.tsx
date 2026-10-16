"use client";

import { useHero, useHeroEvents } from "@/lib/queries";
import { HeroScene } from "./HeroScene";
import { LevelPanel, RecentCard, TodayCard, WeekCard } from "./HomeCards";

/** Home (the pet). Screen only renders it once the hero has loaded. */
export function HomeView() {
  const hero = useHero().data;
  const events = useHeroEvents(30);
  if (!hero) return null;

  return (
    <div className="flex flex-col gap-4 md:flex-row md:flex-wrap md:items-start md:gap-8">
      <HeroScene hero={hero} events={events.data?.events} className="md:flex-[999_1_560px]" />
      <div className="flex min-w-0 flex-col gap-3.5 px-3 md:flex-[1_1_340px] md:gap-6 md:px-0">
        <LevelPanel hero={hero} />
        <TodayCard hero={hero} />
        <WeekCard hero={hero} />
        <RecentCard today={hero.today.date} events={events} />
      </div>
    </div>
  );
}

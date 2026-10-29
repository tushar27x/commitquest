// The contract fixtures (docs/api/fixtures/, the source of truth that web/public/fixtures/ copies),
// plus helpers to read them the way the screens show them.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import type { Hero, HeroEvents, Insights, Preferences } from "../../web/lib/api/types";

const DIR = fileURLToPath(new URL("../../docs/api/fixtures/", import.meta.url));

function load<T>(name: string): T {
  return JSON.parse(readFileSync(`${DIR}${name}.json`, "utf8")) as T;
}

export const fixtures = {
  insights: load<Insights>("insights"),
  hero: load<Hero>("hero"),
  heroEvents: load<HeroEvents>("hero-events"),
  preferences: load<Preferences>("preferences"),
  heroVariants: load<Record<string, Partial<Hero> | string>>("hero-variants"),
};

/** The variants of hero-variants.json (everything but `_doc`). */
export const VARIANTS = Object.keys(fixtures.heroVariants).filter((k) => k !== "_doc");

/** hero.json with a variant merged over it, like the web mock (`applyVariant`: a shallow merge). */
export function heroVariant(name: string): Hero {
  return { ...fixtures.hero, ...(fixtures.heroVariants[name] as Partial<Hero>) };
}

/** Home's words for each mood (components/home/HeroScene.tsx), part of the screen-reader summary. */
const MOOD_WORDS: Record<Hero["mood"], string> = {
  HAPPY: "happy",
  OKAY: "okay",
  WEAK: "weak",
  HUNGRY: "hungry, not fed yet today",
  SLEEPING: "sleeping, it's a rest day",
  LEVEL_UP: "celebrating a level up",
  FAINTED: "fainted, hearts refilled",
};

/** Home's screen-reader summary of the pet, e.g. "Monk, level 7, 2 of 3 hearts, streak 1, okay." */
export function heroSummary(hero: Hero): string {
  const cls = hero.class.current;
  const name = cls.charAt(0) + cls.slice(1).toLowerCase();
  return `${name}, level ${hero.level}, ${hero.lives} of ${hero.max_lives} hearts, streak ${hero.streak}, ${MOOD_WORDS[hero.mood]}.`;
}

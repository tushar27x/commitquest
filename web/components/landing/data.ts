// The landing page's copy and fixed data (docs/design: the "Landing page" boards).

import type { Archetype } from "@/lib/api/types";
import { CLASS_ORDER } from "@/lib/art";

export const NAV_LINKS = [
  { href: "#what", label: "WHAT IT IS" },
  { href: "#play", label: "HOW TO PLAY" },
  { href: "#rules", label: "RULES" },
  { href: "#classes", label: "CLASSES" },
  { href: "#journal", label: "JOURNAL" },
] as const;

/** The four heroes on the title scene. */
export const PARTY: Archetype[] = ["PALADIN", "WIZARD", "BARD", "ROGUE"];

export const PILLARS = [
  {
    accent: "var(--color-class-barbarian)",
    title: "A HERO THAT NEEDS YOU",
    body: "Your hero lives on a Home screen with three hearts. A day with no GitHub activity costs one. Show up and it earns XP, levels up and wins hearts back.",
  },
  {
    accent: "var(--color-class-wizard)",
    title: "A CLASS FROM YOUR HABITS",
    body: "Eight classes are scored from how you really work: streaks, languages, pull request size, commit messages, weekends. Your top score decides who you are.",
  },
  {
    accent: "var(--color-class-cleric)",
    title: "A JOURNAL OF YOUR YEAR",
    body: "Stats, a 28-day calendar, your top repos with AI summaries, and three AI reviews of your craft, written on parchment so data and narrative never blur.",
  },
] as const;

export const STEPS = [
  { title: "PRESS START", body: "Sign in with GitHub. CommitQuest reads your public activity only, never your private repos." },
  { title: "MEET YOUR HERO", body: "Your habits are scored across all 8 classes. The highest becomes your hero, and your past activity seeds your starting XP." },
  { title: "FEED IT DAILY", body: "Any contribution before midnight, your time, counts: commits, pull requests, issues, reviews. Hit SAVE on Home to refresh." },
  { title: "READ THE JOURNAL", body: "Open the Journal for your yearly stats, top repos and AI reviews. Switch class when your habits change." },
] as const;

export const HOME_FEATURES = [
  { label: "HEARTS", color: "#ef4a5a", text: "Three of them. Lose one for each quiet day." },
  { label: "LEVEL + XP", color: "var(--color-gold)", text: "A bar that fills with every active day." },
  { label: "TODAY CARD", color: "var(--color-class-cleric)", text: "Fed or not yet, with a countdown to local midnight." },
  { label: "7-DAY STRIP", color: "var(--color-class-wizard)", text: "Active, missed and resting days at a glance." },
  { label: "MOODS", color: "var(--color-class-bard)", text: "Happy, okay, weak, hungry in the evening, sleeping on rest days." },
] as const;

export const WEEK = [
  { day: "MON", tag: "FED", bg: "var(--color-forest)", fg: "var(--color-bone)" },
  { day: "TUE", tag: "MISS", bg: "#7a2a2a", fg: "var(--color-bone)" },
  { day: "WED", tag: "FED", bg: "var(--color-forest)", fg: "var(--color-bone)" },
  { day: "THU", tag: "FED", bg: "var(--color-forest)", fg: "var(--color-bone)" },
  { day: "FRI", tag: "REST", bg: "#3d3a6b", fg: "var(--color-bone)" },
  { day: "SAT", tag: "FED", bg: "var(--color-forest)", fg: "var(--color-bone)" },
  { day: "SUN", tag: "TODAY", bg: "var(--color-gold)", fg: "var(--color-ink)" },
] as const;

export const RULES = [
  { accent: "#ef4a5a", title: "3 HEARTS", body: "A day with no GitHub activity costs one heart and resets your streak. Any contribution counts: commits, pull requests, issues, reviews." },
  { accent: "var(--color-gold)", title: "+10 XP A DAY", body: "Every active day earns 10 XP. Climbing from level n to n+1 takes 50 × n XP, so your first level-up is just five active days away." },
  { accent: "var(--color-meadow-edge)", title: "HEARTS COME BACK", body: "Every third active day in a row wins a heart back, up to three. Consistency heals." },
  { accent: "var(--color-class-wizard)", title: "LEVEL DOWN, NOT GAME OVER", body: "Lose all three hearts and your hero drops one level, with hearts refilled. At level 1 it only faints. Nothing is ever deleted." },
  { accent: "var(--color-class-paladin)", title: "REST DAYS", body: "Pick up to two weekdays as rest days. A quiet rest day changes nothing, and your streak just waits. Changes apply from the next day." },
  { accent: "var(--color-class-cleric)", title: "A FRESH START", body: "On your first login, your last 90 days of activity seed your XP, so experienced developers do not begin at level 1." },
] as const;

export const TIERS = [
  { name: "CAMPFIRE", range: "Levels 1 to 4", bg: "linear-gradient(#2a1f3d, #5a3a22)" },
  { name: "VILLAGE", range: "Levels 5 to 9", bg: "linear-gradient(#27466b, #2f6b3a)" },
  { name: "KEEP", range: "Levels 10 to 14", bg: "linear-gradient(#3b2d68, #4a4466)" },
  { name: "CITADEL", range: "Level 15 and up", bg: "linear-gradient(#6b4fa8, #8a5a32)" },
] as const;

const CLASS_TEXT: Record<Archetype, { habit: string; signals: string }> = {
  PALADIN: { habit: "Keeps an oath of daily commits: consistent, unbroken streaks", signals: "streaks, active days" },
  BARBARIAN: { habit: "Charges in with huge pull requests and bursts of activity", signals: "PR size, bursts" },
  WIZARD: { habit: "Masters many languages across their projects", signals: "languages used" },
  ARTIFICER: { habit: "Builds the machinery: Docker, CI, infrastructure as code", signals: "infra files in repos" },
  CLERIC: { habit: "Heals codebases: reports issues and lands fixes", signals: "issues, fix commits" },
  BARD: { habit: "Works through pull requests, documents well and draws an audience", signals: "PRs, stars, READMEs" },
  MONK: { habit: "Disciplined: small pull requests and descriptive commit messages", signals: "small PRs, message quality" },
  ROGUE: { habit: "Strikes from the shadows, doing much of their work on weekends", signals: "weekend share" },
};

export const CLASSES = CLASS_ORDER.map((archetype, i) => ({ archetype, num: `NO.${i + 1}`, ...CLASS_TEXT[archetype] }));

export const TRUST = [
  {
    accent: "var(--color-class-cleric)",
    title: "PUBLIC ACTIVITY ONLY",
    body: "Sign in with GitHub and CommitQuest reads your public activity. Your private repos stay private, so private-only work will not show up in your days.",
  },
  {
    accent: "var(--color-class-bard)",
    title: "GENTLE ALERTS",
    body: "An optional email digest, at most once a day and only when something happened, like a heart on the line. Supportive in tone, and you can unsubscribe any time.",
  },
  {
    accent: "var(--color-gold)",
    title: "REST IS PART OF THE GAME",
    body: "Choose up to two rest days a week. Your hero sleeps, your streak waits for you, and nothing is lost.",
  },
] as const;

/** A deterministic scatter of 2 px stars as a `box-shadow` list (the same LCG as the design). */
export function starField(count: number, width: number, height: number, seed: number): string {
  let s = seed;
  const next = () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
  const stars: string[] = [];
  for (let i = 0; i < count; i++) {
    const x = Math.floor(next() * width);
    const y = Math.floor(next() * height);
    stars.push(`${x}px ${y}px #f4ecd8`);
  }
  return stars.join(", ");
}

export const EMBERS = [3, 9, 15, 22, 31, 38, 46, 55, 63, 71, 79, 86, 92, 97].map((left, i) => ({
  left: `${left}%`,
  delay: `${(-(i * 1.7) % 11).toFixed(1)}s`,
  duration: `${(7 + (i % 5) * 1.6).toFixed(1)}s`,
}));

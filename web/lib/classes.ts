// Class copy from the roster board (docs/design/source/character-Roster.dc.html).

import type { Archetype, HeroClass } from "./api/types";
import { scoreRows } from "./journal";
import { CLASS_ORDER } from "./sprites";

export interface ClassInfo {
  /** The one-line habit shown under the class name. */
  habit: string;
  /** What the scorer looks at. */
  signals: string;
  /** Shown while saving: "Tip: Monks score on small PRs." */
  tip: string;
}

export const CLASS_INFO: Record<Archetype, ClassInfo> = {
  PALADIN: { habit: "Keeps an oath of daily commits: consistent, unbroken streaks", signals: "streaks, active days", tip: "Paladins score on streaks and active days." },
  BARBARIAN: { habit: "Charges in with huge pull requests and bursts of activity", signals: "PR size, bursts", tip: "Barbarians score on big PRs and bursts." },
  WIZARD: { habit: "Masters many languages across their projects", signals: "languages used", tip: "Wizards score on the languages they use." },
  ARTIFICER: { habit: "Builds the machinery: Docker, CI, infrastructure as code", signals: "infra files in repos", tip: "Artificers score on Docker, CI and infra files." },
  CLERIC: { habit: "Heals codebases: reports issues and lands fixes", signals: "issues, fix commits", tip: "Clerics score on issues and fix commits." },
  BARD: { habit: "Works through pull requests, documents well and draws an audience", signals: "PRs, stars, READMEs", tip: "Bards score on PRs, stars and READMEs." },
  MONK: { habit: "Disciplined: small pull requests and descriptive commit messages", signals: "small PRs, message quality", tip: "Monks score on small PRs." },
  ROGUE: { habit: "Strikes from the shadows, doing much of their work on weekends", signals: "weekend share", tip: "Rogues score on weekend work." },
};

// What earns each class: the backend scorer (ScoringArchetypeClassifier, ALGO_VERSION 1).
// A score is 0–100, a weighted sum of signals; each signal scales linearly from 0 points at
// `from` to full points at `to`. Shown by the "i" buttons on the Hero sheet.

export interface ScoreSignal {
  /** Share of the class score, in percent. The weights of a class add up to 100. */
  weight: number;
  name: string;
  /** What is measured. */
  measure: string;
  /** 0 points here… */
  from: string;
  /** …full points here. */
  to: string;
}

export interface ClassScoring {
  signals: ScoreSignal[];
  /** What to build to raise the score. */
  tip: string;
  note?: string;
}

export const CLASS_SCORING: Record<Archetype, ClassScoring> = {
  PALADIN: {
    signals: [
      { weight: 60, name: "Showing up", measure: "share of days with a contribution", from: "10%", to: "70%" },
      { weight: 40, name: "Longest streak", measure: "days in a row", from: "3 days", to: "30 days" },
    ],
    tip: "Contribute on most days and keep a long streak.",
  },
  BARBARIAN: {
    signals: [
      { weight: 50, name: "Big pull requests", measure: "average lines changed per PR", from: "300", to: "3,000" },
      { weight: 50, name: "Bursts", measure: "busiest day vs your average active day", from: "3×", to: "10×" },
    ],
    tip: "Ship large PRs and big bursts of work.",
  },
  WIZARD: {
    signals: [{ weight: 100, name: "Languages", measure: "distinct languages across your top repos", from: "2", to: "8" }],
    tip: "Build in more languages.",
  },
  ARTIFICER: {
    signals: [
      { weight: 100, name: "Infrastructure", measure: "top repos with a Dockerfile, CI, Terraform, k8s… at the root", from: "0%", to: "60%" },
    ],
    tip: "Containerise, deploy and automate your projects.",
  },
  CLERIC: {
    signals: [
      { weight: 50, name: "Issues", measure: "issues out of commits + PRs + issues", from: "5%", to: "30%" },
      { weight: 50, name: "Fixes", measure: "your commits whose subject says fix, bug, patch…", from: "10%", to: "50%" },
    ],
    tip: "Report issues and fix bugs.",
  },
  BARD: {
    signals: [
      { weight: 40, name: "Pull requests", measure: "PRs out of commits + PRs", from: "10%", to: "50%" },
      { weight: 30, name: "Audience", measure: "followers + stars on top repos, log scale", from: "10", to: "1,000" },
      { weight: 30, name: "Docs", measure: "top repos with a README of 500+ characters", from: "20%", to: "80%" },
    ],
    tip: "Open PRs, write READMEs, share your work.",
  },
  MONK: {
    signals: [
      { weight: 60, name: "Clear commits", measure: "your commit subjects with 3+ words and 15+ characters", from: "40%", to: "90%" },
      { weight: 40, name: "Small PRs", measure: "average PR size, only if you open PRs", from: "800+ lines", to: "100 lines" },
    ],
    tip: "Keep PRs small and commit messages clear.",
  },
  ROGUE: {
    signals: [{ weight: 100, name: "Weekend work", measure: "Saturday + Sunday share of contributions", from: "30%", to: "60%" }],
    tip: "Do more of your work on weekends.",
    note: "An even week is about 29%, which scores 0. No visible activity at all also makes a Rogue.",
  },
};

// Class switching (plan §2). The backend is the source of truth: `hero.class.qualified`
// decides which rows can be picked, and `PUT /hero/class` answers 409 when one can't.

/** The qualification threshold (backend `insightshub.hero.qualify-score`). */
export const QUALIFY_SCORE = 40;
/** Days between class changes (backend `insightshub.hero.class-cooldown`). */
export const CLASS_COOLDOWN_DAYS = 7;

/** Which classes a set of scores qualifies for: score ≥ 40, plus the top class. Roster order. */
export function qualifiedClasses(scores: Record<Archetype, number>): Archetype[] {
  const top = scoreRows(scores)[0].archetype;
  return CLASS_ORDER.filter((a) => a === top || (scores[a] ?? 0) >= QUALIFY_SCORE);
}

export type ClassTag = "CURRENT" | "TOP SCORE" | "QUALIFIED" | "NEEDS 40";

export interface ClassOption {
  archetype: Archetype;
  score: number;
  /** The top score: the class "follow top score" picks. */
  top: boolean;
  qualified: boolean;
  current: boolean;
  /** Qualified, or the current class (a chosen class that fell below 40 is kept). */
  selectable: boolean;
  tag: ClassTag;
}

/** The Change class list: highest score first, ties in roster order, like the scorer. */
export function classOptions(scores: Record<Archetype, number>, cls: HeroClass): ClassOption[] {
  return scoreRows(scores).map(({ archetype, score }) => {
    const qualified = cls.qualified.includes(archetype);
    const current = archetype === cls.current;
    const top = archetype === cls.scored;
    const tag: ClassTag = current ? "CURRENT" : top ? "TOP SCORE" : qualified ? "QUALIFIED" : `NEEDS ${QUALIFY_SCORE}` as ClassTag;
    return { archetype, score, top, qualified, current, selectable: qualified || current, tag };
  });
}

/** The pick on the Change class screen: a class, or null for "follow top score". */
export type ClassPick = Archetype | null;

/**
 * What sending a pick does (B6). Picking the stored choice again is a no-op (200, no cooldown).
 * Pinning the class you already follow (chosen null, pick = top class) is a real change:
 * CLASS_CHANGED with from == to, and the cooldown starts.
 */
export type ClassAction = "none" | "pin" | "follow" | "become";

export function classAction(cls: HeroClass, picked: ClassPick): ClassAction {
  if (picked === cls.chosen) return "none";
  if (picked === null) return "follow";
  if (cls.chosen === null && picked === cls.scored) return "pin";
  return "become";
}

export interface ClassSuggestion {
  /** Where the habits point now. */
  scored: Archetype;
  /** The chosen class fell below the threshold (it's kept anyway). */
  belowThreshold: boolean;
}

/** "Your habits now point to X": only for a chosen class that isn't the top score. */
export function classSuggestion(cls: HeroClass, scores: Record<Archetype, number>): ClassSuggestion | null {
  const { chosen, scored } = cls;
  if (!chosen || chosen === scored) return null;
  return { scored, belowThreshold: (scores[chosen] ?? 0) < QUALIFY_SCORE };
}

/** Milliseconds until the next change opens; 0 when it's open now. */
export function cooldownLeft(cls: HeroClass, now: number, localUntil = 0): number {
  const until = Math.max(cls.can_switch_at ? Date.parse(cls.can_switch_at) : 0, localUntil);
  return Number.isNaN(until) ? 0 : Math.max(0, until - now);
}

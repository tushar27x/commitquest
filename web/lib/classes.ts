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

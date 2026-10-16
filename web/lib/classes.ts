// Class copy from the roster board (docs/design/source/character-Roster.dc.html).

import type { Archetype } from "./api/types";

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

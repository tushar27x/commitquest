// Class copy from the roster board (docs/design/source/character-Roster.dc.html).

import type { Archetype } from "./api/types.tmp";

export interface ClassInfo {
  /** The one-line habit shown under the class name. */
  habit: string;
  /** What the scorer looks at. */
  signals: string;
}

export const CLASS_INFO: Record<Archetype, ClassInfo> = {
  PALADIN: { habit: "Keeps an oath of daily commits: consistent, unbroken streaks", signals: "streaks, active days" },
  BARBARIAN: { habit: "Charges in with huge pull requests and bursts of activity", signals: "PR size, bursts" },
  WIZARD: { habit: "Masters many languages across their projects", signals: "languages used" },
  ARTIFICER: { habit: "Builds the machinery: Docker, CI, infrastructure as code", signals: "infra files in repos" },
  CLERIC: { habit: "Heals codebases: reports issues and lands fixes", signals: "issues, fix commits" },
  BARD: { habit: "Works through pull requests, documents well and draws an audience", signals: "PRs, stars, READMEs" },
  MONK: { habit: "Disciplined: small pull requests and descriptive commit messages", signals: "small PRs, message quality" },
  ROGUE: { habit: "Strikes from the shadows, doing much of their work on weekends", signals: "weekend share" },
};

// TEMPORARY. The integration agent owns the real types in `web/lib/api/types.ts` (step I1).
// These follow docs/api/contract.md as of I0. When types.ts lands, switch the imports to it
// and delete this file (see the U1 note in docs/agents/HANDOFF.md).

export const ARCHETYPES = [
  "PALADIN",
  "BARBARIAN",
  "WIZARD",
  "ARTIFICER",
  "CLERIC",
  "BARD",
  "MONK",
  "ROGUE",
] as const;
export type Archetype = (typeof ARCHETYPES)[number];

export const WEEKDAYS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;
export type Weekday = (typeof WEEKDAYS)[number];

/** `YYYY-MM-DD` in the user's timezone. */
export type LocalDate = string;
/** ISO-8601 UTC timestamp. */
export type Instant = string;

export type Mood = "HAPPY" | "OKAY" | "WEAK" | "HUNGRY" | "SLEEPING" | "LEVEL_UP" | "FAINTED";
export type BaseMood = "HAPPY" | "OKAY" | "WEAK";
export type Tier = "CAMPFIRE" | "VILLAGE" | "KEEP" | "CITADEL";
export type WeekStatus = "active" | "missed" | "rest" | "today" | "future";
export type HeroEventType =
  | "LIFE_LOST"
  | "LIFE_GAINED"
  | "LEVEL_UP"
  | "LEVEL_DOWN"
  | "FAINTED"
  | "RESTED"
  | "CLASS_CHANGED";

/** RFC 9457 `application/problem+json`. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

// GET /api/user/insights, POST /api/user/sync

export interface RepoCount {
  name: string;
  count: number;
}

export interface RepoDetail {
  name: string;
  readme: string;
  files: string[];
}

export interface UserStats {
  total_commits: number;
  total_prs: number;
  total_issues: number;
  ranked_repos: RepoCount[];
  /** AI input only: never display it. */
  repo_details: RepoDetail[];
  recent_commits: string[];
  code_churn: { additions: number; deletions: number };
  top_languages: string[];
  total_stars: number;
  active_days_per_year: number;
  social_ratio: string;
  last_week_heatmap: number[];
  last_month_heatmap: number[];
  /** Covers only the 7-day week. */
  heatmap_labels: string[];
}

export interface CharacterSheet {
  title: string;
  lore: string;
}

export interface Insights {
  user_name: string;
  archetype: Archetype;
  archetype_scores: Record<Archetype, number>;
  stats: UserStats;
  year_in_review: string;
  weekly_review: string;
  craft_review: string;
  repo_summaries: Record<string, string>;
  character_sheet: CharacterSheet | null;
  updated_at: Instant;
}

// GET /api/user/hero

export interface HeroToday {
  date: LocalDate;
  active: boolean;
  calendar_count: number;
  branch_commit_count: number;
  /** The next local midnight, for the countdown. */
  day_ends_at: Instant;
}

export interface HeroWeekDay {
  date: LocalDate;
  status: WeekStatus;
}

export interface HeroClass {
  chosen: Archetype | null;
  current: Archetype;
  scored: Archetype;
  qualified: Archetype[];
  /** Null when switching is allowed now. */
  can_switch_at: Instant | null;
}

export interface Hero {
  level: number;
  xp: number;
  xp_for_next: number;
  lives: number;
  max_lives: number;
  streak: number;
  streak_for_next_life: number;
  mood: Mood;
  base_mood: BaseMood;
  tier: Tier;
  today: HeroToday;
  /** Monday to Sunday of the current local week. */
  week: HeroWeekDay[];
  timezone: string;
  rest_days: Weekday[];
  class: HeroClass;
  last_evaluated_on: LocalDate;
}

// GET /api/user/hero/events?days=30

export interface HeroEvent {
  type: HeroEventType;
  on_date: LocalDate;
  details: Record<string, unknown>;
}

export interface XpDay {
  on_date: LocalDate;
  xp: number;
  streak: number;
}

export interface HeroEvents {
  /** Newest last. */
  events: HeroEvent[];
  xp_days: XpDay[];
}

// PUT /api/user/hero/class

export interface ChangeClassRequest {
  archetype: Archetype | null;
}

// GET/PUT /api/user/preferences

export interface Preferences {
  timezone: string;
  rest_days: Weekday[];
  alerts_enabled: boolean;
  public_profile: boolean;
}

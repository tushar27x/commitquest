// Shared API types, written from docs/api/contract.md. Owned by the integration agent:
// change this file, the contract and the fixtures together, in one PR.
// `lib/api/fixtures.test.ts` checks every fixture against these types.

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

export const MOODS = ["HAPPY", "OKAY", "WEAK", "HUNGRY", "SLEEPING", "LEVEL_UP", "FAINTED"] as const;
export type Mood = (typeof MOODS)[number];

/** The mood from lives alone: what the sprite shows under an overlay. */
export const BASE_MOODS = ["HAPPY", "OKAY", "WEAK"] as const;
export type BaseMood = (typeof BASE_MOODS)[number];

/** CAMPFIRE 1–4, VILLAGE 5–9, KEEP 10–14, CITADEL 15+. */
export const TIERS = ["CAMPFIRE", "VILLAGE", "KEEP", "CITADEL"] as const;
export type Tier = (typeof TIERS)[number];

export const WEEK_STATUSES = ["active", "missed", "rest", "today", "future"] as const;
export type WeekStatus = (typeof WEEK_STATUSES)[number];

export const HERO_EVENT_TYPES = [
  "LIFE_LOST",
  "LIFE_GAINED",
  "LEVEL_UP",
  "LEVEL_DOWN",
  "FAINTED",
  "RESTED",
  "CLASS_CHANGED",
] as const;
export type HeroEventType = (typeof HERO_EVENT_TYPES)[number];

// Errors (contract.md, "Conventions → Errors")

/**
 * RFC 9457 `application/problem+json`. The backend always sends all five keys, with
 * `type: "about:blank"`, so tell errors apart by status and endpoint, never by text.
 * Optional here because a proxy or network failure may return no body at all.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  /** The request path, without the query string. */
  instance?: string;
}

export interface FieldError {
  /** The snake_case JSON field name, e.g. `rest_days`. */
  field: string;
  message: string;
}

/** A 400 from Bean Validation, e.g. `PUT /api/user/preferences`. */
export interface ValidationProblem extends ProblemDetail {
  errors: FieldError[];
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

export interface CodeChurn {
  additions: number;
  deletions: number;
}

export interface UserStats {
  total_commits: number;
  total_prs: number;
  total_issues: number;
  ranked_repos: RepoCount[];
  /** AI input only: never display it. */
  repo_details: RepoDetail[];
  recent_commits: string[];
  code_churn: CodeChurn;
  top_languages: string[];
  total_stars: number;
  active_days_per_year: number;
  social_ratio: string;
  last_week_heatmap: number[];
  last_month_heatmap: number[];
  /** Covers only the 7-day week. The UI computes 28-day labels from `updated_at`. */
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
  /** `chosen ?? scored`. */
  current: Archetype;
  scored: Archetype;
  qualified: Archetype[];
  /** Null when switching is allowed now. */
  can_switch_at: Instant | null;
}

export interface Hero {
  level: number;
  /** Progress inside the current level. */
  xp: number;
  /** `50 × level`. */
  xp_for_next: number;
  lives: number;
  /** Always 3. */
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
  /** At most 2. */
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
  /** Null goes back to following the top score. */
  archetype: Archetype | null;
}

// GET/PUT /api/user/preferences

export interface Preferences {
  timezone: string;
  rest_days: Weekday[];
  alerts_enabled: boolean;
  public_profile: boolean;
}

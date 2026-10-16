// I1: the fixtures match the shared types, and web/public/fixtures/ is an exact copy of docs/api/fixtures/.
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  ARCHETYPES, BASE_MOODS, HERO_EVENT_TYPES, MOODS, TIERS, WEEK_STATUSES, WEEKDAYS,
  type Hero, type HeroEvents, type Insights, type Preferences,
} from "./types";

const DOCS = join(__dirname, "../../../docs/api/fixtures");
const WEB = join(__dirname, "../../public/fixtures");
const load = (name: string) => JSON.parse(readFileSync(join(DOCS, name), "utf8"));

// A tiny validator whose object shapes are typed against the interfaces: a missing key fails to compile.
type Check = (v: unknown, path: string) => string[];
type Shape<T> = { [K in keyof T]-?: Check };
const ok = (cond: boolean, path: string, what: string) => (cond ? [] : [`${path}: expected ${what}`]);
const int: Check = (v, p) => ok(Number.isInteger(v), p, "integer");
const str: Check = (v, p) => ok(typeof v === "string", p, "string");
const bool: Check = (v, p) => ok(typeof v === "boolean", p, "boolean");
const date: Check = (v, p) => ok(typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v), p, "YYYY-MM-DD");
const instant: Check = (v, p) => ok(typeof v === "string" && /Z$/.test(v) && !isNaN(Date.parse(v)), p, "UTC instant");
const oneOf = (values: readonly string[]): Check => (v, p) => ok(values.includes(v as string), p, values.join("|"));
const nullable = (c: Check): Check => (v, p) => (v === null ? [] : c(v, p));
const array = (c: Check): Check => (v, p) =>
  Array.isArray(v) ? v.flatMap((x, i) => c(x, `${p}[${i}]`)) : [`${p}: expected array`];
const record = (c: Check, keys?: readonly string[]): Check => (v, p) => {
  if (typeof v !== "object" || v === null || Array.isArray(v)) return [`${p}: expected object`];
  const errs = Object.entries(v).flatMap(([k, x]) => c(x, `${p}.${k}`));
  if (keys) errs.push(...ok([...keys].sort().join() === Object.keys(v).sort().join(), p, `keys ${keys.join(",")}`));
  return errs;
};
function obj<T>(shape: Shape<T>): Check {
  return (v, p) => {
    if (typeof v !== "object" || v === null || Array.isArray(v)) return [`${p}: expected object`];
    const o = v as Record<string, unknown>;
    const extra = Object.keys(o).filter((k) => !(k in shape)).map((k) => `${p}.${k}: not in the types`);
    return [...extra, ...Object.entries(shape).flatMap(([k, c]) => (k in o ? (c as Check)(o[k], `${p}.${k}`) : [`${p}.${k}: missing`]))];
  };
}

const archetype = oneOf(ARCHETYPES);
const weekday = oneOf(WEEKDAYS);

const insights = obj<Insights>({
  user_name: str, archetype, archetype_scores: record(int, ARCHETYPES),
  stats: obj<Insights["stats"]>({
    total_commits: int, total_prs: int, total_issues: int,
    ranked_repos: array(obj({ name: str, count: int })),
    repo_details: array(obj({ name: str, readme: str, files: array(str) })),
    recent_commits: array(str), code_churn: obj({ additions: int, deletions: int }),
    top_languages: array(str), total_stars: int, active_days_per_year: int, social_ratio: str,
    last_week_heatmap: array(int), last_month_heatmap: array(int), heatmap_labels: array(str),
  }),
  year_in_review: str, weekly_review: str, craft_review: str, repo_summaries: record(str),
  character_sheet: nullable(obj({ title: str, lore: str })), updated_at: instant,
});

const hero = obj<Hero>({
  level: int, xp: int, xp_for_next: int, lives: int, max_lives: int, streak: int, streak_for_next_life: int,
  mood: oneOf(MOODS), base_mood: oneOf(BASE_MOODS), tier: oneOf(TIERS),
  today: obj<Hero["today"]>({ date, active: bool, calendar_count: int, branch_commit_count: int, day_ends_at: instant }),
  week: array(obj({ date, status: oneOf(WEEK_STATUSES) })),
  timezone: str, rest_days: array(weekday),
  class: obj<Hero["class"]>({
    chosen: nullable(archetype), current: archetype, scored: archetype,
    qualified: array(archetype), can_switch_at: nullable(instant),
  }),
  last_evaluated_on: date,
});

const heroEvents = obj<HeroEvents>({
  events: array(obj({ type: oneOf(HERO_EVENT_TYPES), on_date: date, details: record(() => []) })),
  xp_days: array(obj({ on_date: date, xp: int, streak: int })),
});

const preferences = obj<Preferences>({
  timezone: str, rest_days: array(weekday), alerts_enabled: bool, public_profile: bool,
});

const tierFor = (level: number) => (level >= 15 ? "CITADEL" : level >= 10 ? "KEEP" : level >= 5 ? "VILLAGE" : "CAMPFIRE");
const baseMoodFor = (lives: number) => (lives >= 3 ? "HAPPY" : lives === 2 ? "OKAY" : "WEAK");

/** The contract's invariants for a hero payload. */
function heroInvariants(h: Hero): string[] {
  const errs: string[] = [];
  const check = (cond: boolean, msg: string) => cond || errs.push(msg);
  check(h.xp_for_next === 50 * h.level, `xp_for_next ${h.xp_for_next} != 50 × level ${h.level}`);
  check(h.xp >= 0 && h.xp < h.xp_for_next, `xp ${h.xp} outside the level`);
  check(h.max_lives === 3, "max_lives != 3");
  check(h.lives >= 1 && h.lives <= 3, `lives ${h.lives} outside 1..3`);
  check(h.tier === tierFor(h.level), `tier ${h.tier} != ${tierFor(h.level)} for level ${h.level}`);
  check(h.base_mood === baseMoodFor(h.lives), `base_mood ${h.base_mood} != ${baseMoodFor(h.lives)}`);
  check(h.streak_for_next_life === 3, "streak_for_next_life != 3");
  check(h.rest_days.length <= 2 && new Set(h.rest_days).size === h.rest_days.length, "rest_days: at most 2, unique");
  check(h.week.length === 7, "week must have 7 days");
  h.week.forEach((d, i) => {
    const day = new Date(`${d.date}T00:00:00Z`).getUTCDay(); // 0 = Sunday
    check(day === (i + 1) % 7, `week[${i}] ${d.date} is not ${WEEKDAYS[i]}`);
  });
  check(h.class.current === (h.class.chosen ?? h.class.scored), "class.current != chosen ?? scored");
  check(h.class.qualified.includes(h.class.scored), "the scored class must be qualified");
  return errs;
}

describe("fixtures", () => {
  it("web/public/fixtures/ is an exact copy of docs/api/fixtures/", () => {
    const docs = readdirSync(DOCS).sort();
    expect(readdirSync(WEB).sort()).toEqual(docs);
    for (const f of docs) expect(readFileSync(join(WEB, f), "utf8"), f).toBe(readFileSync(join(DOCS, f), "utf8"));
  });

  it.each([
    ["insights.json", insights],
    ["hero.json", hero],
    ["hero-events.json", heroEvents],
    ["preferences.json", preferences],
  ] as const)("%s matches the types", (name, check) => {
    expect(check(load(name), name)).toEqual([]);
  });

  it("hero.json keeps the contract's invariants", () => {
    expect(heroInvariants(load("hero.json"))).toEqual([]);
  });

  const variants = Object.entries(load("hero-variants.json") as Record<string, object>).filter(([k]) => k !== "_doc");

  it.each(variants)("variant %s, merged over hero.json, is a valid hero", (name, override) => {
    const merged = { ...load("hero.json"), ...override } as Hero;
    expect(hero(merged, name)).toEqual([]);
    expect(heroInvariants(merged)).toEqual([]);
  });
});

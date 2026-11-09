// Mock mode (docs/api/contract.md, "Mock mode"): answers /api/* from web/public/fixtures/.
//
// - NEXT_PUBLIC_MOCK=1 serves every endpoint from fixtures.
// - `?mock=<variant>` merges that entry of hero-variants.json over hero.json.
// - `?mock_arena=hidden|empty` shows the Arena as "Not in the Arena" or with an empty board.
// - `?mock_error=<status>` forces that status on the next call (once per page load; an aborted call
//   doesn't use it up, B38).

import { PRIVATE_LOCKED_TITLE, type Archetype, type FieldError, type Hero, type HeroEvent, type HeroEvents, type Leaderboard, type Preferences, type ProblemDetail, type ValidationProblem } from "./api/types";

export const MOCK_MODE = process.env.NEXT_PUBLIC_MOCK === "1";

/**
 * Mock mode plays the fixture story: it is Friday 16 Oct 2026, 18:20 in Asia/Kolkata
 * (plan §5), and the clock runs on from there. Otherwise it's the real time.
 * (`lib/clock.ts` re-exports it for components.)
 */
const MOCK_STORY_NOW = Date.parse("2026-10-16T12:50:00Z");
const loadedAt = Date.now();

export function clockNow(): number {
  return MOCK_MODE ? MOCK_STORY_NOW + (Date.now() - loadedAt) : Date.now();
}

const DAY_MS = 86_400_000;
/** The class cooldown (contract: 7 days). */
const CLASS_COOLDOWN_MS = 7 * DAY_MS;
/** The private-access lock (B11: 7 days). */
const PRIVATE_LOCK_MS = 7 * DAY_MS;

/** Endpoints the backend doesn't have yet. Remove an entry once its step merges. */
const PENDING_ENDPOINTS: readonly string[] = [];

const SYNC_DELAY_MS = 3_000;
/** 7:31, the countdown shown in the design. */
const MOCK_RETRY_AFTER_S = 451;

const ERROR_TITLES: Record<number, string> = {
  400: "Bad Request",
  500: "Internal Server Error",
  401: "Unauthorized",
  404: "Not Found",
  409: "Conflict",
  429: "Too Many Requests",
  502: "Bad Gateway",
};

/** Whether a request to this path is answered by the mock instead of the backend. */
export function servedByMock(path: string, method = "GET"): boolean {
  if (MOCK_MODE) return true;
  const { pathname } = new URL(path, "http://mock");
  return PENDING_ENDPOINTS.includes(`${method.toUpperCase()} ${pathname}`);
}

type FixtureName = "insights" | "hero" | "hero-variants" | "hero-events" | "preferences" | "preferences-variants" | "leaderboard" | "rank" | "rank-not-in-arena";

const fixtureCache = new Map<FixtureName, Promise<unknown>>();

function fixture<T>(name: FixtureName): Promise<T> {
  let cached = fixtureCache.get(name);
  if (!cached) {
    cached = fetch(`/fixtures/${name}.json`).then((res) => {
      if (!res.ok) throw new Error(`mock: fixture ${name}.json returned ${res.status}`);
      return res.json();
    });
    cached.catch(() => fixtureCache.delete(name));
    fixtureCache.set(name, cached);
  }
  return cached as Promise<T>;
}

function searchParam(name: string): string | null {
  if (typeof window === "undefined") return null;
  return new URLSearchParams(window.location.search).get(name);
}

/**
 * `?mock_error` fails one call per page load. A call only claims it; the error counts as used
 * once its response is delivered. If the call is aborted first, the claim is released for the
 * next one: React StrictMode's double mount in `next dev` cancels the first fetch of every
 * query and fetches again (B38).
 */
let forcedError: "unused" | "claimed" | "used" = "unused";

function claimForcedError(signal?: AbortSignal | null): number | null {
  if (forcedError !== "unused" || signal?.aborted) return null;
  const status = Number(searchParam("mock_error"));
  if (!Number.isInteger(status) || status < 400 || status > 599) return null;
  forcedError = "claimed";
  signal?.addEventListener(
    "abort",
    () => {
      if (forcedError === "claimed") forcedError = "unused";
    },
    { once: true },
  );
  return status;
}

/** Shallow merge is enough: variants replace nested objects (`today`, `class`) whole. */
export function applyVariant(
  hero: Hero,
  variants: Record<string, unknown>,
  variant: string | null,
): Hero {
  if (!variant || variant === "_doc") return hero;
  const patch = variants[variant];
  if (!patch || typeof patch !== "object") {
    console.warn(`mock: unknown ?mock=${variant}, using hero.json`);
    return hero;
  }
  return { ...hero, ...(patch as Partial<Hero>) };
}

async function mockHero(): Promise<Hero> {
  const [hero, variants] = await Promise.all([
    fixture<Hero>("hero"),
    fixture<Record<string, unknown>>("hero-variants"),
  ]);
  return applyVariant(hero, variants, searchParam("mock"));
}

let savedPreferences: Preferences | null = null;

/**
 * B11: `?mock=<variant>` merges that entry of preferences-variants.json over preferences.json:
 * `private_off_granted`, `private_granted`, `private_reauth`, `private_locked_on`, `private_locked_off`,
 * `private_lock_lost_scope`. Other variants (the hero's) leave the preferences alone.
 */
export function applyPreferencesVariant(prefs: Preferences, variants: Record<string, unknown>, variant: string | null): Preferences {
  if (!variant || variant === "_doc") return prefs;
  const patch = variants[variant];
  if (!patch || typeof patch !== "object") return prefs;
  return { ...prefs, ...(patch as Partial<Preferences>) };
}

async function mockPreferences(): Promise<Preferences> {
  if (savedPreferences) return savedPreferences;
  const [prefs, variants] = await Promise.all([fixture<Preferences>("preferences"), fixture<Record<string, unknown>>("preferences-variants")]);
  return applyPreferencesVariant(prefs, variants, searchParam("mock"));
}

/** What the mock `PUT /preferences` does with a body, given what it serves now (contract B11, "order of checks"). */
export type PreferencesPut =
  | { kind: "locked"; retryAfter: number }
  | { kind: "saved"; saved: Preferences; resync: boolean };

export function decidePreferencesPut(current: Preferences, body: Preferences, now: number): PreferencesPut {
  const changes = body.include_private !== current.include_private;
  const until = current.private_locked_until ? Date.parse(current.private_locked_until) : NaN;
  const locked = !Number.isNaN(until) && until > now;
  // The one exception: turning off while waiting for GitHub changes nothing effective.
  const waitingOff = current.include_private && current.private_access === "none" && !body.include_private;
  if (changes && locked && !waitingOff) return { kind: "locked", retryAfter: Math.max(1, Math.ceil((until - now) / 1000)) };
  // private_access and private_locked_until are read-only: the server's own values win.
  const effective = changes && current.private_access === "granted";
  return {
    kind: "saved",
    resync: effective,
    saved: {
      ...body,
      private_access: current.private_access,
      private_locked_until: effective ? new Date(now + PRIVATE_LOCK_MS).toISOString() : current.private_locked_until,
    },
  };
}
let chosenClass: { archetype: Archetype | null; canSwitchAt: string } | null = null;
/** CLASS_CHANGED events from mock class changes, appended to hero-events.json. */
const classEvents: HeroEvent[] = [];

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Same shape as the backend (B2): `type` is always about:blank, `instance` the request path. */
function problem(
  status: number,
  detail: string,
  instance: string,
  { retryAfter = MOCK_RETRY_AFTER_S, errors, title }: { retryAfter?: number; errors?: FieldError[]; title?: string } = {},
): Response {
  const body: ProblemDetail | ValidationProblem = {
    type: "about:blank",
    title: title ?? ERROR_TITLES[status] ?? "Error",
    status,
    detail: errors ? "The request is invalid; see 'errors'" : `[mock] ${detail}`,
    instance,
    ...(errors ? { errors } : {}),
  };
  const headers: Record<string, string> = { "Content-Type": "application/problem+json" };
  if (status === 429) headers["Retry-After"] = String(retryAfter);
  return new Response(JSON.stringify(body), { status, headers });
}

/** The backend's checks for `PUT /preferences` (B5): every field required, an IANA zone, at most 2 distinct days. */
export function validatePreferences(body: Partial<Preferences>): FieldError[] {
  const errors: FieldError[] = [];
  const required = ["timezone", "rest_days", "alerts_enabled", "public_profile", "include_private"] as const;
  for (const field of required) {
    if (body[field] === undefined || body[field] === null) errors.push({ field, message: "must not be null" });
  }
  if (typeof body.timezone === "string" && !isIanaZone(body.timezone)) {
    errors.push({ field: "timezone", message: "must be an IANA time zone, like Europe/Berlin" });
  }
  if (Array.isArray(body.rest_days)) {
    if (body.rest_days.length > 2) errors.push({ field: "rest_days", message: "at most 2 rest days" });
    if (new Set(body.rest_days).size !== body.rest_days.length) {
      errors.push({ field: "rest_days", message: "must not repeat a day" });
    }
  }
  return errors;
}

/**
 * Like the backend's `@IanaTimeZone`: any zone id Java knows (`Asia/Calcutta`, `Etc/GMT+5`, `UTC`),
 * but no offsets (`+05:30`, `GMT+5`, `UTC-3`). Intl stands in for `ZoneId.getAvailableZoneIds()`.
 */
export function isIanaZone(zone: string): boolean {
  if (/^[+-]/.test(zone) || /^(GMT|UTC?|UT)[+-]/i.test(zone)) return false;
  try {
    new Intl.DateTimeFormat("en", { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

function delay(ms: number, signal?: AbortSignal | null): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(signal.reason);
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => {
      clearTimeout(timer);
      reject(signal.reason);
    });
  });
}

function readBody<T>(init: RequestInit): T | null {
  if (typeof init.body !== "string") return null;
  try {
    return JSON.parse(init.body) as T;
  } catch {
    return null;
  }
}

/** A `fetch` stand-in for /api/* paths. */
export async function mockFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const { pathname } = new URL(path, "http://mock");

  if (method === "POST" && pathname === "/api/user/sync") {
    await delay(SYNC_DELAY_MS, init.signal);
  }

  const forced = claimForcedError(init.signal);
  if (forced !== null) {
    // Let a StrictMode cleanup abort this call before the error is spent (rejects if aborted).
    await delay(0, init.signal);
    forcedError = "used";
    return problem(forced, `forced by ?mock_error=${forced}`, pathname);
  }

  switch (`${method} ${pathname}`) {
    case "GET /api/user/insights":
    case "POST /api/user/sync":
      return json(await fixture("insights"));

    case "GET /api/user/hero":
      return json(withChosenClass(await mockHero()));

    case "PUT /api/user/hero/class": {
      const hero = withChosenClass(await mockHero());
      const archetype = readBody<{ archetype?: Archetype | null }>(init)?.archetype ?? null;
      // Picking the stored choice again changes nothing: 200, no cooldown (B6).
      if (archetype === hero.class.chosen) return json(hero);
      // Not qualified wins over the cooldown: waiting wouldn't help (B6).
      if (archetype !== null && !hero.class.qualified.includes(archetype)) {
        return problem(409, `${archetype} isn't qualified`, pathname);
      }
      const wait = hero.class.can_switch_at ? Date.parse(hero.class.can_switch_at) - clockNow() : 0;
      if (wait > 0) {
        return problem(429, "The class was changed recently", pathname, { retryAfter: Math.ceil(wait / 1000) });
      }
      // Pinning the class you already follow is a change too: from == to, and the cooldown starts.
      classEvents.push({
        type: "CLASS_CHANGED",
        on_date: hero.today.date,
        details: { from: hero.class.current, to: archetype ?? hero.class.scored, chosen: archetype },
      });
      chosenClass = { archetype, canSwitchAt: new Date(clockNow() + CLASS_COOLDOWN_MS).toISOString() };
      return json(withChosenClass(hero));
    }

    case "GET /api/user/hero/events": {
      const story = await fixture<HeroEvents>("hero-events");
      return json({ ...story, events: [...story.events, ...classEvents] });
    }

    case "GET /api/user/preferences":
      return json(await mockPreferences());

    case "PUT /api/user/preferences": {
      const body = readBody<Partial<Preferences>>(init);
      if (!body) return problem(400, "body is not JSON", pathname);
      const errors = validatePreferences(body);
      if (errors.length > 0) return problem(400, "invalid preferences", pathname, { errors });
      const decision = decidePreferencesPut(await mockPreferences(), body as Preferences, clockNow());
      if (decision.kind === "locked") {
        return problem(429, "Private repo access was changed recently", pathname, { retryAfter: decision.retryAfter, title: PRIVATE_LOCKED_TITLE });
      }
      // An effective flip resyncs inside the request, like POST /sync.
      if (decision.resync) await delay(SYNC_DELAY_MS, init.signal);
      savedPreferences = decision.saved;
      return json(savedPreferences);
    }

    case "GET /api/leaderboard": {
      const board = await fixture<Leaderboard>("leaderboard");
      const archetype = (new URL(path, "http://mock").searchParams.get("archetype") as Archetype | null) ?? board.archetype;
      // The fixture is the MONK board. `?mock_arena=empty`, or any other class, is a board nobody joined.
      if (searchParam("mock_arena") === "empty" || archetype !== board.archetype) {
        return json({ ...board, archetype, page: 0, total: 0, entries: [] });
      }
      return json(board);
    }

    case "GET /api/user/rank": {
      // `?mock_arena=hidden`: a private profile, the "Not in the Arena" 404 (a problem+json body).
      if (searchParam("mock_arena") === "hidden") {
        return new Response(JSON.stringify(await fixture("rank-not-in-arena")), {
          status: 404,
          headers: { "Content-Type": "application/problem+json" },
        });
      }
      return json(await fixture("rank"));
    }

    default:
      return problem(404, `no fixture for ${method} ${pathname}`, pathname);
  }
}

function withChosenClass(hero: Hero): Hero {
  if (!chosenClass) return hero;
  const { archetype, canSwitchAt } = chosenClass;
  return {
    ...hero,
    class: { ...hero.class, chosen: archetype, current: archetype ?? hero.class.scored, can_switch_at: canSwitchAt },
  };
}

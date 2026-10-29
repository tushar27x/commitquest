// Mock mode (docs/api/contract.md, "Mock mode"): answers /api/* from web/public/fixtures/.
//
// - NEXT_PUBLIC_MOCK=1 serves every endpoint from fixtures.
// - `?mock=<variant>` merges that entry of hero-variants.json over hero.json.
// - `?mock_error=<status>` forces that status on the next call (once per page load).

import type { Archetype, FieldError, Hero, HeroEvent, HeroEvents, Preferences, ProblemDetail, ValidationProblem } from "./api/types";

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

type FixtureName = "insights" | "hero" | "hero-variants" | "hero-events" | "preferences";

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

let forcedErrorUsed = false;

function takeForcedError(): number | null {
  if (forcedErrorUsed) return null;
  const status = Number(searchParam("mock_error"));
  if (!Number.isInteger(status) || status < 400 || status > 599) return null;
  forcedErrorUsed = true;
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
  { retryAfter = MOCK_RETRY_AFTER_S, errors }: { retryAfter?: number; errors?: FieldError[] } = {},
): Response {
  const body: ProblemDetail | ValidationProblem = {
    type: "about:blank",
    title: ERROR_TITLES[status] ?? "Error",
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
  const required = ["timezone", "rest_days", "alerts_enabled", "public_profile"] as const;
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

  const forced = takeForcedError();
  if (forced !== null) return problem(forced, `forced by ?mock_error=${forced}`, pathname);

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
      return json(savedPreferences ?? (await fixture<Preferences>("preferences")));

    case "PUT /api/user/preferences": {
      const body = readBody<Partial<Preferences>>(init);
      if (!body) return problem(400, "body is not JSON", pathname);
      const errors = validatePreferences(body);
      if (errors.length > 0) return problem(400, "invalid preferences", pathname, { errors });
      savedPreferences = body as Preferences;
      return json(savedPreferences);
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

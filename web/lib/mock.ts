// Mock mode (docs/api/contract.md, "Mock mode"): answers /api/* from web/public/fixtures/.
//
// - NEXT_PUBLIC_MOCK=1 serves every endpoint from fixtures.
// - The hero and preferences endpoints don't exist yet (backend B5), so they are always
//   served from fixtures until NEXT_PUBLIC_HERO_API=1.
// - `?mock=<variant>` merges that entry of hero-variants.json over hero.json.
// - `?mock_error=<status>` forces that status on the next call (once per page load).

import type { Archetype, Hero, Preferences, ProblemDetail } from "./api/types.tmp";

export const MOCK_MODE = process.env.NEXT_PUBLIC_MOCK === "1";
const HERO_API_LIVE = process.env.NEXT_PUBLIC_HERO_API === "1";

const PENDING_ENDPOINTS = ["/api/user/hero", "/api/user/preferences"];

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
export function servedByMock(path: string): boolean {
  if (MOCK_MODE) return true;
  if (HERO_API_LIVE) return false;
  const { pathname } = new URL(path, "http://mock");
  return PENDING_ENDPOINTS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
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
let chosenClass: { archetype: Archetype | null } | null = null;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Same shape as the backend (B2): `type` is always about:blank, `instance` the request path. */
function problem(status: number, detail: string, instance: string): Response {
  const body: ProblemDetail = {
    type: "about:blank",
    title: ERROR_TITLES[status] ?? "Error",
    status,
    detail: `[mock] ${detail}`,
    instance,
  };
  const headers: Record<string, string> = { "Content-Type": "application/problem+json" };
  if (status === 429) headers["Retry-After"] = String(MOCK_RETRY_AFTER_S);
  return new Response(JSON.stringify(body), { status, headers });
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
      const archetype = readBody<{ archetype: Archetype | null }>(init)?.archetype ?? null;
      if (archetype !== null && !hero.class.qualified.includes(archetype)) {
        return problem(409, `${archetype} is not qualified`, pathname);
      }
      chosenClass = { archetype };
      return json(withChosenClass(hero));
    }

    case "GET /api/user/hero/events":
      return json(await fixture("hero-events"));

    case "GET /api/user/preferences":
      return json(savedPreferences ?? (await fixture<Preferences>("preferences")));

    case "PUT /api/user/preferences": {
      const body = readBody<Partial<Preferences>>(init);
      if (!body) return problem(400, "body is not JSON", pathname);
      savedPreferences = { ...(await fixture<Preferences>("preferences")), ...body };
      return json(savedPreferences);
    }

    default:
      return problem(404, `no fixture for ${method} ${pathname}`, pathname);
  }
}

function withChosenClass(hero: Hero): Hero {
  if (!chosenClass) return hero;
  const { archetype } = chosenClass;
  return {
    ...hero,
    class: { ...hero.class, chosen: archetype, current: archetype ?? hero.class.scored },
  };
}

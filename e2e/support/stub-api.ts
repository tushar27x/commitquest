// A fake backend inside the browser for the `stubbed` project: Playwright answers every /api/*
// call of the production build from the contract fixtures, and a test overrides single endpoints
// to force the error states (contract.md: problem+json, 429 with Retry-After).
import { randomBytes } from "node:crypto";
import type { Page, Request, Route } from "@playwright/test";
import { fixtures } from "./fixtures";

export interface Reply {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}

/** Answers one call: a fixed reply, or a function of the request (e.g. a different reply per call). */
export type Handler = Reply | ((request: Request) => Reply | "abort" | Promise<Reply>);

/** Keys like "GET /api/user/hero" or "POST /api/user/sync". */
export type Overrides = Record<string, Handler>;

const DEFAULTS: Overrides = {
  "GET /api/user/insights": { body: fixtures.insights },
  "POST /api/user/sync": { body: fixtures.insights },
  "GET /api/user/hero": { body: fixtures.hero },
  "GET /api/user/hero/events": { body: fixtures.heroEvents },
  "GET /api/user/preferences": { body: fixtures.preferences },
  "PUT /api/user/preferences": (request) => ({ body: request.postDataJSON() }),
  "PUT /api/user/hero/class": { body: fixtures.hero },
};

const TITLES: Record<number, string> = {
  400: "Bad Request",
  401: "Unauthorized",
  404: "Not Found",
  409: "Conflict",
  429: "Too Many Requests",
  502: "Bad Gateway",
};

/** A problem+json reply shaped like the backend's (B2): `type` about:blank, `instance` = path. */
export function problem(status: number, path: string, extra: { retryAfter?: number; errors?: unknown[] } = {}): Reply {
  const headers: Record<string, string> = { "Content-Type": "application/problem+json" };
  if (extra.retryAfter !== undefined) headers["Retry-After"] = String(extra.retryAfter);
  return {
    status,
    headers,
    body: {
      type: "about:blank",
      title: TITLES[status] ?? "Error",
      status,
      detail: `e2e: forced ${status}`,
      instance: path,
      ...(extra.errors ? { errors: extra.errors } : {}),
    },
  };
}

/** Each reply in turn, then the last one again: e.g. a failure, then success on RETRY. */
export function sequence(...replies: Reply[]): Handler {
  let i = 0;
  return () => replies[Math.min(i++, replies.length - 1)];
}

/** Every /api call the page made, for assertions on headers and bodies. */
export interface ApiLog {
  calls: Request[];
  /** Calls to one endpoint, e.g. `log.of("PUT /api/user/preferences")`. */
  of(key: string): Request[];
}

export async function stubApi(page: Page, overrides: Overrides = {}): Promise<ApiLog> {
  const handlers = { ...DEFAULTS, ...overrides };
  const calls: Request[] = [];
  await page.route("**/api/**", async (route: Route) => {
    const request = route.request();
    const { pathname } = new URL(request.url());
    const key = `${request.method()} ${pathname}`;
    calls.push(request);
    const handler = handlers[key];
    const reply = typeof handler === "function" ? await handler(request) : (handler ?? problem(404, pathname));
    if (reply === "abort") return route.abort("connectionrefused");
    await route.fulfill({
      status: reply.status ?? 200,
      headers: reply.headers ?? { "Content-Type": "application/json" },
      body: JSON.stringify(reply.body ?? null),
    });
  });
  return {
    calls,
    of: (key) => calls.filter((r) => `${r.method()} ${new URL(r.url()).pathname}` === key),
  };
}

function base64url(value: string | Buffer): string {
  return Buffer.from(value).toString("base64url");
}

/**
 * A JWT-shaped string, built at runtime (B31: nothing token-shaped in the repo). Unsigned in effect:
 * the stubbed backend never checks it, and the web app only reads `exp` and `name`.
 */
export function fakeJwt(claims: { sub?: string; name?: string; expiresInS?: number } = {}): string {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64url(
    JSON.stringify({ iss: "insightshub", sub: claims.sub ?? "9000902", name: claims.name ?? "e2e", iat: now, exp: now + (claims.expiresInS ?? 3600) }),
  );
  return `${header}.${payload}.${base64url(randomBytes(32))}`;
}

/** Logs in through the real `/dashboard#token=…` hand-off and waits for Home. */
export async function login(page: Page, token: string = fakeJwt()): Promise<string> {
  await page.goto(`/dashboard#token=${token}`);
  await page.waitForURL("**/home");
  return token;
}

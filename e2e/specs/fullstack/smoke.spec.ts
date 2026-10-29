// Full stack: the production build against the real backend (src/test/.../it/E2eBackend.java):
// Postgres + Redis on Testcontainers, GitHub answered in-process (GitHubStub), the LLM unreachable
// (every AI text is its static fallback). The browser's trip to GitHub's authorize page is the only
// part stubbed here. Nothing reaches GitHub, Groq or Neon.
//
// One user, one login: the tests run in order and share it (the login sync claims the 10-minute cooldown).
import { expect, test, type Page } from "@playwright/test";
import { BACKEND_URL, BROWSER_ZONE, WEB_URL } from "../../support/env";

test.describe.configure({ mode: "serial" });

let page: Page;
let token: string;
const apiUrls: string[] = [];

/** The API as the logged-in user, through the web app's /api rewrite. */
async function api(path: string, init: { method?: string; data?: unknown } = {}) {
  const res = await page.request.fetch(`${WEB_URL}${path}`, {
    method: init.method ?? "GET",
    headers: { Authorization: `Bearer ${token}` },
    data: init.data,
  });
  return { status: res.status(), headers: res.headers(), body: await res.json() };
}

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage({ timezoneId: BROWSER_ZONE });
  page.on("request", (request) => apiUrls.push(request.url()));
  // Nothing may reach the real GitHub: a safety net for every browser request to it.
  await page.route(/^https:\/\/([a-z0-9-]+\.)*github\.com\//, (route) => route.abort("blockedbyclient"));
  // GitHub's authorize page, approved at once. Playwright doesn't intercept redirect targets, so the
  // backend's /login is fetched here: keep its state cookie, and send the browser from GitHub's URL
  // (redirect_uri + state) straight back to the callback with a code.
  await page.route(`${BACKEND_URL}/api/auth/login`, async (route) => {
    const login = await route.fetch({ maxRedirects: 0 });
    const authorize = new URL(login.headers()["location"]);
    expect(authorize.origin + authorize.pathname).toBe("https://github.com/login/oauth/authorize");
    const callback = new URL(authorize.searchParams.get("redirect_uri")!);
    callback.searchParams.set("code", "e2e-code");
    callback.searchParams.set("state", authorize.searchParams.get("state")!);
    await route.fulfill({ status: 302, headers: { ...login.headers(), location: callback.toString() } });
  });
});

test.afterAll(async () => {
  await page.close();
});

test("PRESS START → GitHub → callback → /dashboard#token → Home, with the hero from the first sync", async () => {
  await page.goto("/");
  const start = page.getByRole("link", { name: "PRESS START" });
  await expect(start).toHaveAttribute("href", `${BACKEND_URL}/api/auth/login`);
  await start.click();
  await page.waitForURL(`${WEB_URL}/home`, { timeout: 120_000 }); // the login sync runs inside the callback

  token = (await page.evaluate(() => window.localStorage.getItem("ih.token")))!;
  expect(token).toBeTruthy();
  expect(page.url()).not.toContain("token");
  // The fragment never left the browser: no request URL, to the web app or the backend, carries the token.
  expect(apiUrls.filter((url) => url.includes(token))).toEqual([]);
  // A new hero: all 3 hearts (the seed's level comes from the synced history).
  await expect(page.getByText(/, level \d+, 3 of 3 hearts, streak \d+, /)).toBeAttached();
});

test("the browser's zone replaced the default UTC (contract, browser zone on login)", async () => {
  await expect.poll(async () => (await api("/api/user/preferences")).body.timezone).toBe(BROWSER_ZONE);
  const hero = (await api("/api/user/hero")).body;
  expect(hero.timezone).toBe(BROWSER_ZONE);
});

test("Journal and Hero sheet render the synced insights (AI texts are the static fallbacks)", async () => {
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "JOURNAL" }).click();
  await expect(page.getByRole("heading", { name: "YEAR IN REVIEW" })).toBeVisible();
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "HERO" }).click();
  await expect(page.getByRole("region", { name: "Your character" })).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "THE ROAD IS BLOCKED" })).toHaveCount(0);
});

test("SAVE right after login rests: the login sync claimed the cooldown (429 + Retry-After from the backend)", async () => {
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "HOME" }).click();
  await page.getByRole("button", { name: "SAVE", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Save\. Resting, ready in \d+:\d\d$/ })).toBeDisabled();
});

test("Settings saves to the backend and the change survives a reload", async () => {
  const before = (await api("/api/user/preferences")).body;
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "SETTINGS" }).click();
  const alerts = page.getByRole("switch", { name: "EMAIL ALERTS" });
  await alerts.click();
  await expect(page.getByRole("status").filter({ hasText: "SAVED" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("switch", { name: "EMAIL ALERTS" })).toHaveAttribute("aria-checked", String(!before.alerts_enabled));
  expect((await api("/api/user/preferences")).body).toEqual({ ...before, alerts_enabled: !before.alerts_enabled });
});

test("errors come back as problem+json through the rewrite", async () => {
  const bad = await api("/api/user/preferences", { method: "PUT", data: { timezone: "+05:30" } });
  expect(bad.status).toBe(400);
  expect(bad.headers["content-type"]).toContain("application/problem+json");
  expect(bad.body.errors.map((e: { field: string }) => e.field)).toEqual(
    expect.arrayContaining(["timezone", "rest_days", "alerts_enabled", "public_profile"]),
  );
  const res = await page.request.get(`${WEB_URL}/api/user/hero`); // no token
  expect(res.status()).toBe(401);
});

// The login hand-off (contract.md "Conventions", I4): the backend redirects to /dashboard#token=<jwt>,
// the web app stores it and leaves with router.replace. The token must not stay in the address bar
// or history, and must never be sent anywhere except in the Authorization header.
import { expect, test } from "@playwright/test";
import { fixtures, heroSummary } from "../../support/fixtures";
import { BROWSER_ZONE } from "../../support/env";
import { fakeJwt, login, stubApi } from "../../support/stub-api";

test("the #token hand-off stores the token, leaves no trace in the URL and sends it only as a header", async ({ page }) => {
  const log = await stubApi(page);
  const urls: string[] = [];
  page.on("request", (request) => urls.push(request.url()));
  const token = fakeJwt();

  await login(page, token);

  await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
  expect(page.url()).not.toContain("token");
  expect(await page.evaluate(() => window.location.hash)).toBe("");
  // Every request the browser made, navigations included: the token is in none of the URLs.
  expect(urls.filter((url) => url.includes(token))).toEqual([]);
  // Every API call carried it as a Bearer token.
  expect(log.calls.length).toBeGreaterThan(0);
  for (const call of log.calls) expect(await call.headerValue("authorization")).toBe(`Bearer ${token}`);
  // router.replace: going back doesn't return to /dashboard#token=…
  await page.goBack();
  expect(page.url()).not.toContain(token);
});

test("the token survives a reload; without one, a tab screen goes back to the title", async ({ page }) => {
  await stubApi(page);
  await login(page);
  await page.reload();
  await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();

  await page.evaluate(() => window.localStorage.clear());
  await page.goto("/journal");
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("link", { name: "PRESS START" })).toBeVisible();
});

test("an expired token in the fragment isn't kept: back to the title", async ({ page }) => {
  await stubApi(page);
  await page.goto(`/dashboard#token=${fakeJwt({ expiresInS: -60 })}`);
  await expect(page).toHaveURL(/\/$/);
});

test.describe("browser zone on login (contract, GET/PUT /preferences)", () => {
  test("a stored UTC is replaced by the browser's zone: one PUT, the full object, only timezone changed", async ({ page }) => {
    const stored = { ...fixtures.preferences, timezone: "UTC" };
    let putDoneAt = Infinity;
    const heroReadAt: number[] = [];
    const log = await stubApi(page, {
      "GET /api/user/preferences": { body: stored },
      // Slow on purpose, so Home has mounted and read the hero before the PUT is done.
      "PUT /api/user/preferences": async (request) => {
        await new Promise((r) => setTimeout(r, 500));
        putDoneAt = Date.now();
        return { body: request.postDataJSON() };
      },
      "GET /api/user/hero": () => {
        heroReadAt.push(Date.now());
        return { body: fixtures.hero };
      },
    });
    const put = page.waitForRequest((r) => r.method() === "PUT" && r.url().endsWith("/api/user/preferences"));
    await login(page);
    expect((await put).postDataJSON()).toEqual({ ...stored, timezone: BROWSER_ZONE });
    // The hero is read again once the PUT is done: its dates and `day_ends_at` follow the new zone.
    await expect.poll(() => heroReadAt.some((t) => t >= putDoneAt)).toBe(true);
    expect(log.of("PUT /api/user/preferences")).toHaveLength(1);
  });

  test("a zone the user picked is never overwritten", async ({ page }) => {
    const log = await stubApi(page); // preferences.json: Asia/Kolkata
    await login(page);
    await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
    await expect.poll(() => log.of("GET /api/user/preferences").length).toBeGreaterThanOrEqual(1);
    await page.waitForTimeout(500); // give a wrong PUT the chance to show up
    expect(log.of("PUT /api/user/preferences")).toHaveLength(0);
  });

  test("a failed PUT is ignored: Home still loads", async ({ page }) => {
    await stubApi(page, {
      "GET /api/user/preferences": { body: { ...fixtures.preferences, timezone: "UTC" } },
      "PUT /api/user/preferences": { status: 502, body: { status: 502 } },
    });
    await login(page);
    await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
  });
});

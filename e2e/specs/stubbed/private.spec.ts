// SHARE PRIVATE REPOS on the real API client (contract B11): the lock 429, every other error of the PUT,
// the wire format, what an effective flip refetches, and the private re-auth hand-off.
// /api/* is answered by Playwright, so each test forces exactly the server state it needs.
import { expect, test, type Page } from "@playwright/test";
import type { Preferences } from "../../../web/lib/api/types";
import { BACKEND_URL, BROWSER_ZONE } from "../../support/env";
import { fixtures } from "../../support/fixtures";
import { fakeJwt, login, problem, stubApi, type Overrides } from "../../support/stub-api";

const PREFS = "/api/user/preferences";
const DAY_MS = 86_400_000;

const granted: Preferences = { ...fixtures.preferences, include_private: false, private_access: "granted", private_locked_until: null };
const lockedIn = (days: number) => new Date(Date.now() + days * DAY_MS).toISOString();
/** The lock date as the page prints it ("23 Oct 2026", upper-cased in the help line). */
const dateLabel = (iso: string) => new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: BROWSER_ZONE }).format(new Date(iso));

const priv = (page: Page) => page.getByRole("switch", { name: "SHARE PRIVATE REPOS" });
const outcome = (page: Page, text: string | RegExp) => page.getByRole("status").filter({ hasText: text });

/** Logs in, opens Settings, flips the switch through the warning and CONFIRMs. */
async function flip(page: Page, kind: "on" | "off" = "on") {
  await page.goto("/settings");
  await expect(priv(page)).toBeEnabled();
  await priv(page).click();
  const dialog = page.getByRole("dialog", { name: kind === "on" ? "SHARE PRIVATE REPOS?" : "STOP SHARING PRIVATE REPOS?" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("checkbox", { name: "I understand" }).check();
  await dialog.getByRole("button", { name: "CONFIRM" }).click();
}

test.describe("the warning sends nothing until CONFIRM", () => {
  test("CANCEL and Esc: no PUT, the switch stays as it was", async ({ page }) => {
    const log = await stubApi(page, { [`GET ${PREFS}`]: { body: granted } });
    await login(page);
    await page.goto("/settings");
    await priv(page).click();
    await page.getByRole("checkbox", { name: "I understand" }).check();
    await page.getByRole("button", { name: "CANCEL" }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await priv(page).click();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await page.waitForTimeout(500); // give a stray PUT the chance to show up
    expect(log.of(`PUT ${PREFS}`)).toHaveLength(0);
  });

  test("waiting for GitHub (true/none) turns off at once: no dialog, a plain PUT", async ({ page }) => {
    const log = await stubApi(page, { [`GET ${PREFS}`]: { body: { ...fixtures.preferences, include_private: true, private_access: "none" } } });
    await login(page);
    await page.goto("/settings");
    await priv(page).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(outcome(page, "SAVED")).toBeVisible();
    expect(log.of(`PUT ${PREFS}`)).toHaveLength(1);
    expect(log.of(`PUT ${PREFS}`)[0].postDataJSON()).toMatchObject({ include_private: false });
  });
});

test.describe("errors of the flipping PUT: nothing changed, the switch is as it was", () => {
  test("429 titled Private access locked: preferences are re-read and the lock shows, switch disabled", async ({ page }) => {
    const until = lockedIn(3);
    let locked = false;
    const log = await stubApi(page, {
      [`GET ${PREFS}`]: () => ({ body: locked ? { ...granted, private_locked_until: until } : granted }),
      [`PUT ${PREFS}`]: () => {
        locked = true; // another tab changed it first
        return problem(429, PREFS, { retryAfter: 3 * 86_400, title: "Private access locked" });
      },
    });
    await login(page);
    await flip(page);
    await expect(outcome(page, `NOT CHANGED · LOCKED UNTIL ${dateLabel(until).toUpperCase()}`)).toBeVisible();
    await expect(priv(page)).toBeDisabled();
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await expect(page.locator("#private-lock")).toContainText(dateLabel(until));
    // The 429 made the cached preferences stale: they were read again.
    await expect.poll(() => log.of(`GET ${PREFS}`).length).toBeGreaterThanOrEqual(3);
  });

  test("plain 429 Too Many Requests: a rate limit, not the lock: try again later, switch still usable", async ({ page }) => {
    await stubApi(page, {
      [`GET ${PREFS}`]: { body: granted },
      [`PUT ${PREFS}`]: problem(429, PREFS, { retryAfter: 45 }),
    });
    await login(page);
    await flip(page);
    await expect(outcome(page, /NOT CHANGED · TOO MANY TRIES\. TRY AGAIN IN/)).toBeVisible();
    await expect(page.getByText(/LOCKED UNTIL/)).toHaveCount(0);
    await expect(priv(page)).toBeEnabled();
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
  });

  test("409: GITHUB NEEDS YOU TO LOG IN AGAIN with RECONNECT GITHUB to the backend login", async ({ page }) => {
    await stubApi(page, { [`GET ${PREFS}`]: { body: granted }, [`PUT ${PREFS}`]: problem(409, PREFS) });
    await login(page);
    await flip(page);
    const status = outcome(page, "NOT CHANGED · GITHUB NEEDS YOU TO LOG IN AGAIN");
    await expect(status).toBeVisible();
    await expect(status.getByRole("link", { name: "RECONNECT GITHUB" })).toHaveAttribute("href", `${BACKEND_URL}/api/auth/login`);
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await expect(priv(page)).toBeEnabled();
  });

  test("502: GITHUB IS UNAVAILABLE, nothing changed, and a second try is possible", async ({ page }) => {
    await stubApi(page, { [`GET ${PREFS}`]: { body: granted }, [`PUT ${PREFS}`]: problem(502, PREFS) });
    await login(page);
    await flip(page);
    await expect(outcome(page, "NOT CHANGED · GITHUB IS UNAVAILABLE")).toBeVisible();
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await expect(priv(page)).toBeEnabled();
    await expect(page.getByText("RESYNCING YOUR INSIGHTS…")).toHaveCount(0);
  });
});

test.describe("a successful effective flip", () => {
  test("the PUT carries the full object (private_locked_until is read-only, the server's value wins)", async ({ page }) => {
    const until = lockedIn(7);
    const log = await stubApi(page, {
      [`GET ${PREFS}`]: { body: granted },
      [`PUT ${PREFS}`]: (request) => ({ body: { ...request.postDataJSON(), private_access: "granted", private_locked_until: until } }),
    });
    await login(page);
    await flip(page);
    await expect(outcome(page, "PRIVATE REPOS ARE ON · INSIGHTS RESYNCED")).toBeVisible();
    const [put] = log.of(`PUT ${PREFS}`);
    // All five writable fields plus the two read-only ones the web app read: the server ignores those.
    expect(put.postDataJSON()).toEqual({ ...granted, include_private: true });
    expect(Object.keys(put.postDataJSON()).sort()).toEqual(
      ["alerts_enabled", "include_private", "private_access", "private_locked_until", "public_profile", "rest_days", "timezone"],
    );
    // The screen shows what the server answered, not what was sent (null).
    await expect(priv(page)).toBeDisabled();
    await expect(page.locator("#private-help")).toHaveText(`LOCKED UNTIL ${dateLabel(until).toUpperCase()}`);
  });

  /** Counts the GETs of one data screen after navigating in the SPA (a full load would refetch anyway). */
  function reads(log: Awaited<ReturnType<typeof stubApi>>) {
    return {
      insights: () => log.of("GET /api/user/insights").length,
      hero: () => log.of("GET /api/user/hero").length,
      events: () => log.of("GET /api/user/hero/events").length,
    };
  }
  const nav = (page: Page, name: string) => page.getByRole("navigation", { name: "Main" }).getByRole("link", { name }).click();

  test("insights, hero and events are fetched again afterwards", async ({ page }) => {
    const overrides: Overrides = {
      [`GET ${PREFS}`]: { body: granted },
      [`PUT ${PREFS}`]: (request) => ({ body: { ...request.postDataJSON(), private_locked_until: lockedIn(7) } }),
    };
    const log = await stubApi(page, overrides);
    await login(page);
    await nav(page, "JOURNAL");
    await expect(page.getByRole("heading", { name: "YEAR IN REVIEW" })).toBeVisible();
    await nav(page, "SETTINGS");
    await expect(priv(page)).toBeEnabled();
    const before = { insights: reads(log).insights(), hero: reads(log).hero(), events: reads(log).events() };
    expect(before.insights).toBeGreaterThan(0);
    expect(before.events).toBeGreaterThan(0);

    await priv(page).click();
    await page.getByRole("checkbox", { name: "I understand" }).check();
    await page.getByRole("button", { name: "CONFIRM" }).click();
    await expect(outcome(page, "PRIVATE REPOS ARE ON · INSIGHTS RESYNCED")).toBeVisible();

    await nav(page, "JOURNAL");
    await expect(page.getByRole("heading", { name: "YEAR IN REVIEW" })).toBeVisible();
    await expect.poll(() => reads(log).insights()).toBeGreaterThan(before.insights);
    await nav(page, "HOME"); // Home reads the hero and the hero events
    await expect.poll(() => reads(log).events()).toBeGreaterThan(before.events);
    await expect.poll(() => reads(log).hero()).toBeGreaterThan(before.hero);
  });

  test("a stored wish (true/none) is not an effective change: insights and events are not refetched", async ({ page }) => {
    const none: Preferences = { ...fixtures.preferences, include_private: false, private_access: "none" };
    const log = await stubApi(page, {
      [`GET ${PREFS}`]: { body: none },
      [`PUT ${PREFS}`]: (request) => ({ body: { ...request.postDataJSON(), private_access: "none", private_locked_until: null } }),
    });
    await login(page);
    await nav(page, "JOURNAL");
    await expect(page.getByRole("heading", { name: "YEAR IN REVIEW" })).toBeVisible();
    await nav(page, "SETTINGS");
    const before = { insights: reads(log).insights(), events: reads(log).events() };
    await priv(page).click();
    await page.getByRole("checkbox", { name: "I understand" }).check();
    await page.getByRole("button", { name: "CONFIRM" }).click();
    await expect(outcome(page, "SAVED")).toBeVisible();
    await expect(page.getByRole("link", { name: "AUTHORIZE ON GITHUB" })).toBeVisible();
    await nav(page, "JOURNAL");
    await expect(page.getByRole("heading", { name: "YEAR IN REVIEW" })).toBeVisible();
    await page.waitForTimeout(500);
    expect({ insights: reads(log).insights(), events: reads(log).events() }).toEqual(before);
  });
});

test.describe("private re-auth hand-off (sessionStorage ih_private_reauth)", () => {
  /** Marks a private re-auth in this tab, then arrives the way the backend's callback redirects. */
  async function comeBack(page: Page, prefs: Preferences, mark = true) {
    await stubApi(page, { [`GET ${PREFS}`]: { body: prefs } });
    await page.goto("/");
    if (mark) await page.evaluate(() => window.sessionStorage.setItem("ih_private_reauth", "1"));
    const token = fakeJwt();
    await page.goto(`/dashboard#token=${token}`);
    return token;
  }
  const settingsUrl = (page: Page) => page.waitForURL((url) => url.pathname === "/settings" && url.search === "");

  test("with the marker the hand-off lands on /settings, not /home; the marker and the ?private=back param are gone", async ({ page }) => {
    const token = await comeBack(page, { ...fixtures.preferences, include_private: true, private_access: "granted", private_locked_until: lockedIn(7) });
    await settingsUrl(page);
    await expect(outcome(page, "GITHUB SAID YES · PRIVATE REPOS ARE ON")).toBeVisible();
    expect(page.url()).not.toContain("private=back");
    expect(page.url()).not.toContain(token);
    expect(await page.evaluate(() => window.sessionStorage.getItem("ih_private_reauth"))).toBeNull();
    // The outcome is not repeated on a reload: the param is gone.
    await page.reload();
    await expect(priv(page)).toBeVisible();
    await expect(outcome(page, "GITHUB SAID YES")).toHaveCount(0);
  });

  test("granted but the lock kept it off: the permission was recorded, access stays off", async ({ page }) => {
    const until = lockedIn(4);
    await comeBack(page, { ...fixtures.preferences, include_private: false, private_access: "granted", private_locked_until: until });
    await settingsUrl(page);
    await expect(outcome(page, `LOCKED UNTIL ${dateLabel(until).toUpperCase()} · THE PERMISSION WAS RECORDED, BUT ACCESS STAYS OFF`)).toBeVisible();
    await expect(priv(page)).toBeDisabled();
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
  });

  test("GitHub did not share the scope (true/none): declined, with AUTHORIZE ON GITHUB to try again", async ({ page }) => {
    await comeBack(page, { ...fixtures.preferences, include_private: true, private_access: "none", private_locked_until: null });
    await settingsUrl(page);
    await expect(outcome(page, "GITHUB DIDN'T SHARE YOUR PRIVATE REPOS")).toBeVisible();
    await expect(page.getByRole("link", { name: "AUTHORIZE ON GITHUB" })).toHaveAttribute("href", `${BACKEND_URL}/api/auth/login?private=true`);
  });

  test("clicking AUTHORIZE ON GITHUB leaves the marker, so the next hand-off returns to Settings", async ({ page }) => {
    await stubApi(page, { [`GET ${PREFS}`]: { body: { ...fixtures.preferences, include_private: true, private_access: "none" } } });
    await login(page);
    await page.goto("/settings");
    // Don't leave the app: cancel the link's own navigation, React's onClick (the marker) still runs.
    await page.getByRole("link", { name: "AUTHORIZE ON GITHUB" }).evaluate((a) => a.addEventListener("click", (e) => e.preventDefault()));
    await page.getByRole("link", { name: "AUTHORIZE ON GITHUB" }).click();
    expect(await page.evaluate(() => window.sessionStorage.getItem("ih_private_reauth"))).toBe("1");
  });
});

// The system states of plan §4 on the real API client, with each endpoint failing on its own:
// 401 / 404 / 409 / 429 + Retry-After / 502 / network, on SAVE, the shell's GETs, change class and settings.
import { expect, test, type Page } from "@playwright/test";
import { fixtures, heroSummary } from "../../support/fixtures";
import { BACKEND_URL } from "../../support/env";
import { login, problem, sequence, stubApi } from "../../support/stub-api";

const SYNC = "/api/user/sync";
const save = (page: Page) => page.getByRole("button", { name: "SAVE", exact: true }).click();
const roadBlocked = (page: Page) => page.getByRole("alert").filter({ hasText: "THE ROAD IS BLOCKED" });

test.describe("SAVE (POST /sync)", () => {
  test("429 + Retry-After: SAVE rests with a countdown, and it survives a reload", async ({ page }) => {
    await stubApi(page, { [`POST ${SYNC}`]: problem(429, SYNC, { retryAfter: 451 }) });
    await login(page);
    await save(page);
    const resting = page.getByRole("button", { name: /^Save\. Resting, ready in 7:(31|30|29)$/ });
    await expect(resting).toBeDisabled();
    await page.reload();
    await expect(page.getByRole("button", { name: /^Save\. Resting, ready in 7:[23]\d$/ })).toBeDisabled();
  });

  test("409: YOUR GITHUB SEAL BROKE, with RECONNECT GITHUB to the backend login; the data stays", async ({ page }) => {
    await stubApi(page, { [`POST ${SYNC}`]: problem(409, SYNC) });
    await login(page);
    await save(page);
    const banner = page.getByRole("alert").filter({ hasText: "YOUR GITHUB SEAL BROKE" });
    await expect(banner).toBeVisible();
    await expect(banner.getByRole("link", { name: "RECONNECT GITHUB" })).toHaveAttribute("href", `${BACKEND_URL}/api/auth/login`);
    await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
  });

  test("502: THE ROAD IS BLOCKED, and RETRY saves again", async ({ page }) => {
    const log = await stubApi(page, { [`POST ${SYNC}`]: sequence(problem(502, SYNC), { body: fixtures.insights }) });
    await login(page);
    await save(page);
    await expect(roadBlocked(page)).toBeVisible();
    await roadBlocked(page).getByRole("button", { name: "RETRY" }).click();
    await expect(roadBlocked(page)).toBeHidden();
    expect(log.of(`POST ${SYNC}`)).toHaveLength(2);
  });

  test("a network error is THE ROAD IS BLOCKED too", async ({ page }) => {
    await stubApi(page, { [`POST ${SYNC}`]: () => "abort" });
    await login(page);
    await save(page);
    await expect(roadBlocked(page)).toBeVisible();
  });

  test("401: SESSION ENDED, the token is dropped, BACK TO TITLE", async ({ page }) => {
    await stubApi(page, { [`POST ${SYNC}`]: problem(401, SYNC) });
    await login(page);
    await save(page);
    const dialog = page.getByRole("dialog", { name: "SESSION ENDED" });
    await expect(dialog).toBeVisible();
    expect(await page.evaluate(() => window.localStorage.getItem("ih.token"))).toBeNull();
    await dialog.getByRole("button", { name: "BACK TO TITLE" }).click();
    await expect(page).toHaveURL(/\/$/);
  });

  test("404: NEW GAME", async ({ page }) => {
    await stubApi(page, { [`POST ${SYNC}`]: problem(404, SYNC) });
    await login(page);
    await save(page);
    await expect(page.getByText("NEW GAME")).toBeVisible();
  });
});

test.describe("the shell's GETs", () => {
  test("404 on insights before the first sync: NEW GAME, and ROLL CHARACTER loads the hero", async ({ page }) => {
    await stubApi(page, {
      "GET /api/user/insights": sequence(problem(404, "/api/user/insights"), { body: fixtures.insights }),
      "GET /api/user/hero": sequence(problem(404, "/api/user/hero"), { body: fixtures.hero }),
    });
    await login(page);
    await expect(page.getByText("NEW GAME")).toBeVisible();
    await page.getByRole("button", { name: "ROLL CHARACTER" }).click();
    await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
  });

  test("a 502 that outlasts the automatic retry: THE ROAD IS BLOCKED, then RETRY", async ({ page }) => {
    const hero = "/api/user/hero";
    await stubApi(page, { [`GET ${hero}`]: sequence(problem(502, hero), problem(502, hero), { body: fixtures.hero }) });
    await login(page);
    await expect(roadBlocked(page)).toBeVisible();
    await roadBlocked(page).getByRole("button", { name: "RETRY" }).click();
    await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
  });

  test("401 on a GET: SESSION ENDED", async ({ page }) => {
    await stubApi(page, { "GET /api/user/hero": problem(401, "/api/user/hero") });
    await login(page);
    await expect(page.getByRole("dialog", { name: "SESSION ENDED" })).toBeVisible();
  });
});

test.describe("change class (PUT /hero/class)", () => {
  const CLASS = "/api/user/hero/class";
  const pickRogueAndConfirm = async (page: Page) => {
    await page.goto("/hero/change");
    await page.getByRole("button", { name: /^Rogue, score \d+, qualified$/ }).click();
    await page.getByRole("button", { name: "BECOME ROGUE" }).click();
  };

  test("200: the new class, and the request body", async ({ page }) => {
    const changed = { ...fixtures.hero, class: { ...fixtures.hero.class, chosen: "ROGUE", current: "ROGUE" } };
    const log = await stubApi(page, { [`PUT ${CLASS}`]: { body: changed } });
    await login(page);
    await pickRogueAndConfirm(page);
    await expect(page.getByText("You are now a Rogue. Level and hearts kept.")).toBeVisible();
    expect(log.of(`PUT ${CLASS}`)[0].postDataJSON()).toEqual({ archetype: "ROGUE" });
  });

  test("409: the class doesn't qualify any more", async ({ page }) => {
    await stubApi(page, { [`PUT ${CLASS}`]: problem(409, CLASS) });
    await login(page);
    await pickRogueAndConfirm(page);
    await expect(page.getByText(/^Rogue doesn't qualify any more/)).toBeVisible();
  });

  test("429 + Retry-After: the cooldown note and a disabled confirm button", async ({ page }) => {
    await stubApi(page, { [`PUT ${CLASS}`]: problem(429, CLASS, { retryAfter: 3 * 86_400 }) });
    await login(page);
    await pickRogueAndConfirm(page);
    await expect(page.getByText("Your class changed recently. Wait for the cooldown to end.")).toBeVisible();
    await expect(page.getByRole("button", { name: /^NEXT CHANGE IN / })).toBeDisabled();
  });

  test("502: THE ROAD IS BLOCKED", async ({ page }) => {
    await stubApi(page, { [`PUT ${CLASS}`]: problem(502, CLASS) });
    await login(page);
    await pickRogueAndConfirm(page);
    await expect(roadBlocked(page)).toBeVisible();
  });
});

test.describe("settings (PUT /preferences)", () => {
  const PREFS = "/api/user/preferences";
  const toggleAlerts = (page: Page) => page.getByRole("switch", { name: "EMAIL ALERTS" }).click();
  const status = (page: Page) => page.getByRole("status").filter({ hasText: /SAVED|SAVING|TOO MANY|NOT SAVED/ });

  test("200: SAVED, with the full object sent", async ({ page }) => {
    const log = await stubApi(page);
    await login(page);
    await page.goto("/settings");
    await toggleAlerts(page);
    await expect(status(page)).toHaveText("SAVED");
    expect(log.of(`PUT ${PREFS}`).at(-1)!.postDataJSON()).toEqual({
      ...fixtures.preferences,
      alerts_enabled: !fixtures.preferences.alerts_enabled,
    });
  });

  test("400 with errors[]: NOT SAVED", async ({ page }) => {
    await stubApi(page, {
      [`PUT ${PREFS}`]: problem(400, PREFS, { errors: [{ field: "rest_days", message: "at most 2 rest days" }] }),
    });
    await login(page);
    await page.goto("/settings");
    await toggleAlerts(page);
    await expect(status(page)).toContainText("NOT SAVED");
  });

  test("429 + Retry-After: TOO MANY CHANGES with a countdown", async ({ page }) => {
    await stubApi(page, { [`PUT ${PREFS}`]: problem(429, PREFS, { retryAfter: 42 }) });
    await login(page);
    await page.goto("/settings");
    await toggleAlerts(page);
    await expect(status(page)).toContainText(/TOO MANY CHANGES · TRY AGAIN IN 0:(42|41|40)/);
  });
});

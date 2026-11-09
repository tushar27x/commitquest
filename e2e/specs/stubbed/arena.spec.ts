// The Arena on the real API client (production build, /api/* stubbed): GET /user/rank and
// GET /leaderboard?archetype=…&page=…, from the contract fixtures, and each state of plan §4.
import { expect, test } from "@playwright/test";
import { fixtures } from "../../support/fixtures";
import { login, problem, stubApi } from "../../support/stub-api";

const RANK = "/api/user/rank";
const BOARD = "/api/leaderboard";
const nav = (page: import("@playwright/test").Page) => page.getByRole("navigation", { name: "Main" });
const roadBlocked = (page: import("@playwright/test").Page) => page.getByRole("alert").filter({ hasText: "THE ROAD IS BLOCKED" });

test("the board renders with your (you) row, and the calls carry archetype and page", async ({ page }) => {
  const log = await stubApi(page);
  await login(page);
  await nav(page).getByRole("link", { name: "ARENA" }).click();
  await expect(page).toHaveURL(/\/arena$/);
  await expect(page.getByRole("heading", { name: `${fixtures.leaderboard.archetype} BOARD` })).toBeVisible();
  const rows = page.getByRole("listitem").filter({ has: page.locator("img") });
  await expect(rows).toHaveCount(fixtures.leaderboard.entries.length);
  const you = fixtures.leaderboard.entries.find((e) => e.is_you)!;
  await expect(rows.filter({ hasText: "(you)" })).toHaveCount(1);
  await expect(rows.filter({ hasText: "(you)" })).toContainText(you.login);
  await expect(page.getByText(`Top ${fixtures.rank.top_percent}% of ${fixtures.rank.total} heroes`)).toBeVisible();
  const call = new URL(log.of(`GET ${BOARD}`)[0].url());
  expect(call.searchParams.get("archetype")).toBe(fixtures.leaderboard.archetype);
  expect(call.searchParams.get("page")).toBe("0");
});

test("another class's board is asked for by its archetype, and an empty one says so", async ({ page }) => {
  const log = await stubApi(page);
  await login(page);
  await page.goto("/arena");
  await page.getByRole("button", { name: /^Wizard board/ }).click();
  await expect(page.getByRole("heading", { name: "WIZARD BOARD" })).toBeVisible();
  await expect(page.getByText(/board is empty\./)).toBeVisible();
  expect(log.of(`GET ${BOARD}`).some((r) => new URL(r.url()).searchParams.get("archetype") === "WIZARD")).toBe(true);
});

test("an empty board for your own class invites you to join", async ({ page }) => {
  await stubApi(page, { [`GET ${BOARD}`]: { body: { ...fixtures.leaderboard, total: 0, entries: [] } } });
  await login(page);
  await page.goto("/arena");
  await expect(page.getByText(/board is empty\. Be the first/)).toBeVisible();
});

test("404 Not in the Arena: a card with the detail verbatim and a link to Settings, not an error", async ({ page }) => {
  await stubApi(page, { [`GET ${RANK}`]: { status: 404, headers: { "Content-Type": "application/problem+json" }, body: fixtures.rankNotInArena } });
  await login(page);
  await page.goto("/arena");
  await expect(page.getByRole("heading", { name: "NOT IN THE ARENA" })).toBeVisible();
  await expect(page.getByText(fixtures.rankNotInArena.detail!, { exact: true })).toBeVisible();
  await expect(roadBlocked(page)).toHaveCount(0);
  await page.getByRole("link", { name: /SHOW ME IN SETTINGS/ }).click();
  await expect(page).toHaveURL(/\/settings$/);
});

test("5xx on the board: THE ROAD IS BLOCKED, and RETRY loads it", async ({ page }) => {
  let failing = true;
  await stubApi(page, { [`GET ${BOARD}`]: () => (failing ? problem(502, BOARD) : { body: fixtures.leaderboard }) });
  await login(page);
  await page.goto("/arena");
  const card = roadBlocked(page);
  await expect(card).toBeVisible();
  failing = false;
  await card.getByRole("button", { name: "RETRY" }).click();
  await expect(card).toBeHidden();
  await expect(page.getByRole("heading", { name: `${fixtures.leaderboard.archetype} BOARD` })).toBeVisible();
  await expect(page.getByRole("listitem").filter({ hasText: "ironvow" })).toBeVisible();
});

test("5xx on the rank: THE ROAD IS BLOCKED, and RETRY shows your place", async ({ page }) => {
  let failing = true;
  await stubApi(page, { [`GET ${RANK}`]: () => (failing ? problem(502, RANK) : { body: fixtures.rank }) });
  await login(page);
  await page.goto("/arena");
  const card = roadBlocked(page);
  await expect(card).toBeVisible();
  failing = false;
  await card.getByRole("button", { name: "RETRY" }).click();
  await expect(page.getByRole("heading", { name: "YOUR PLACE" })).toBeVisible();
});

test("429 with Retry-After on the board is a blocked road too", async ({ page }) => {
  await stubApi(page, { [`GET ${BOARD}`]: problem(429, BOARD, { retryAfter: 30 }) });
  await login(page);
  await page.goto("/arena");
  await expect(roadBlocked(page)).toBeVisible();
});

test("Settings: the Arena switch sends public_profile in the PUT", async ({ page }) => {
  const log = await stubApi(page);
  await login(page);
  await page.goto("/settings");
  await page.getByRole("switch", { name: "SHOW ME IN THE ARENA" }).click();
  await expect(page.getByRole("status").filter({ hasText: "SAVED" })).toBeVisible();
  expect(log.of("PUT /api/user/preferences")[0].postDataJSON()).toEqual({
    ...fixtures.preferences,
    public_profile: !fixtures.preferences.public_profile,
  });
});

// Mock mode (NEXT_PUBLIC_MOCK=1): every screen renders from the fixtures, and the tabs link them.
import { expect, test } from "@playwright/test";
import { fixtures, heroSummary } from "../../support/fixtures";

const nav = (page: import("@playwright/test").Page) => page.getByRole("navigation", { name: "Main" });

test("PRESS START on the title goes home, where the hero from hero.json waits", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "PRESS START" }).first().click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
  await expect(page.getByRole("heading", { level: 1, name: "HOME" })).toBeAttached();
});

for (const tab of [
  { link: "JOURNAL", path: "/journal", landmark: { role: "heading" as const, name: "YEAR IN REVIEW" } },
  { link: "HERO", path: "/hero", landmark: { role: "region" as const, name: "Your character" } },
  { link: "ARENA", path: "/arena", landmark: { role: "region" as const, name: "Choose a board" } },
  { link: "SETTINGS", path: "/settings", landmark: { role: "region" as const, name: "Notifications and privacy" } },
]) {
  test(`the ${tab.link} tab opens its screen`, async ({ page }) => {
    await page.goto("/home");
    await nav(page).getByRole("link", { name: tab.link }).click();
    await expect(page).toHaveURL(new RegExp(`${tab.path}$`));
    await expect(page.getByRole("heading", { level: 1, name: tab.link })).toBeVisible();
    await expect(page.getByRole(tab.landmark.role, { name: tab.landmark.name })).toBeVisible();
  });
}

test("the phone tab bar has all five tabs and moves between screens", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/home");
  const tabs = nav(page);
  for (const name of ["HOME", "JOURNAL", "HERO", "ARENA", "SETTINGS"]) {
    await expect(tabs.getByRole("link", { name })).toBeVisible();
  }
  await tabs.getByRole("link", { name: "HERO" }).click();
  await expect(page).toHaveURL(/\/hero$/);
  await expect(page.getByRole("region", { name: "Your character" })).toBeVisible();
});

test("Settings has the Arena switch, and it saves", async ({ page }) => {
  await page.goto("/settings");
  const arena = page.getByRole("switch", { name: "SHOW ME IN THE ARENA" });
  await expect(arena).toBeVisible();
  await expect(arena).toHaveAttribute("aria-checked", String(fixtures.preferences.public_profile));
  // The mock answers in the page (no network request to watch); the PUT body is checked in specs/stubbed/arena.spec.ts.
  await arena.click();
  await expect(page.getByRole("status").filter({ hasText: "SAVED" })).toBeVisible();
  await expect(arena).toHaveAttribute("aria-checked", String(!fixtures.preferences.public_profile));
});

test("the Arena shows the MONK board with the (you) row and your place", async ({ page }) => {
  await page.goto("/arena");
  await expect(page.getByRole("heading", { name: `${fixtures.leaderboard.archetype} BOARD` })).toBeVisible();
  await expect(page.getByRole("listitem").filter({ hasText: "(you)" })).toHaveCount(1);
  await expect(page.getByRole("heading", { name: "YOUR PLACE" })).toBeVisible();
  await expect(page.getByText(`Top ${fixtures.rank.top_percent}% of ${fixtures.rank.total} heroes`)).toBeVisible();
});

test("?mock_arena=hidden: NOT IN THE ARENA, with a link to Settings", async ({ page }) => {
  await page.goto("/arena?mock_arena=hidden");
  await expect(page.getByRole("heading", { name: "NOT IN THE ARENA" })).toBeVisible();
  await expect(page.getByText(fixtures.rankNotInArena.detail!)).toBeVisible();
  await expect(page.getByRole("link", { name: /SHOW ME IN SETTINGS/ })).toHaveAttribute("href", "/settings");
});

test("?mock_arena=empty: an empty board", async ({ page }) => {
  await page.goto("/arena?mock_arena=empty");
  await expect(page.getByText(/board is empty\./)).toBeVisible();
});

test("Change class lists every class, highest score first", async ({ page }) => {
  await page.goto("/hero/change");
  await expect(page.getByRole("heading", { level: 1, name: "CHANGE CLASS" })).toBeVisible();
  const rows = page.getByRole("list", { name: "Classes, highest score first" }).getByRole("listitem");
  await expect(rows).toHaveCount(8);
  await expect(rows.first()).toContainText(fixtures.hero.class.scored);
});

test("a mock class change goes through and starts the cooldown", async ({ page }) => {
  await page.goto("/hero/change");
  const other = fixtures.hero.class.qualified.find((c) => c !== fixtures.hero.class.current)!;
  const name = other.charAt(0) + other.slice(1).toLowerCase();
  await page.getByRole("button", { name: new RegExp(`^${name}, score \\d+, qualified$`) }).click();
  await page.getByRole("button", { name: `BECOME ${other}` }).click();
  await expect(page.getByText(`You are now a ${name}. Level and hearts kept.`)).toBeVisible();
  await expect(page.getByText(/CLASS COOLDOWN · /)).toBeVisible();
});

test("SAVE shows the saving overlay, then the screen again", async ({ page }) => {
  await page.goto("/home");
  await page.getByRole("button", { name: "SAVE", exact: true }).click();
  await expect(page.getByText("SAVING YOUR QUEST…")).toBeVisible();
  await expect(page.getByText("SAVING YOUR QUEST…")).toBeHidden({ timeout: 10_000 });
  await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached();
});

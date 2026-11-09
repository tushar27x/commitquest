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

for (const [label, viewport] of [
  ["desktop", { width: 1280, height: 800 }],
  ["phone", { width: 375, height: 812 }],
] as const) {
  test(`a link on every screen goes back to the landing page (${label})`, async ({ page }) => {
    await page.setViewportSize(viewport);
    for (const path of ["/home", "/journal", "/hero", "/arena", "/settings"]) {
      await page.goto(path);
      await expect(page.getByRole("link", { name: "Back to the landing page" })).toBeVisible();
    }
    await page.getByRole("link", { name: "Back to the landing page" }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole("link", { name: "PRESS START" }).first()).toBeVisible();
  });
}

test("the phone Hero header keeps its title centred with the back link in it", async ({ page }) => {
  for (const width of [360, 390, 430]) {
    await page.setViewportSize({ width, height: 800 });
    await page.goto("/hero");
    const title = await page.getByRole("heading", { level: 1, name: "HERO" }).boundingBox();
    expect(title, `HERO title at ${width} px`).not.toBeNull();
    expect(Math.abs(title!.x + title!.width / 2 - width / 2), `title centre at ${width} px`).toBeLessThan(2);
    await expect(page.getByRole("link", { name: "Back to the landing page" })).toBeVisible();
    await expect(page.getByRole("link", { name: /CHANGE/ })).toBeVisible();
  }
});

test("Change class has its own BACK and no link to the landing page", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/hero/change");
  await expect(page.getByRole("heading", { name: "CHANGE CLASS" }).first()).toBeVisible();
  await expect(page.getByRole("link", { name: "Back to the landing page" })).toHaveCount(0);
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

test("Settings: SHARE PRIVATE REPOS on shows AUTHORIZE ON GITHUB, linked to the mock grant", async ({ page }) => {
  await page.goto("/settings");
  const priv = page.getByRole("switch", { name: "SHARE PRIVATE REPOS" });
  await expect(priv).toHaveAttribute("aria-checked", "false");
  await priv.click();
  // B11/U13: the switch asks first; nothing is sent before CONFIRM (the full flow: specs/mock/private.spec.ts).
  const dialog = page.getByRole("dialog", { name: "SHARE PRIVATE REPOS?" });
  await dialog.getByRole("checkbox", { name: "I understand" }).check();
  await dialog.getByRole("button", { name: "CONFIRM" }).click();
  await expect(page.getByRole("status").filter({ hasText: "SAVED" })).toBeVisible();
  await expect(page.getByText("ON · NEEDS GITHUB PERMISSION")).toBeVisible();
  await expect(page.getByRole("link", { name: "AUTHORIZE ON GITHUB" })).toHaveAttribute("href", "/settings?mock=private_granted&private=back");
  // Mock mode's "GitHub" is a link back with ?private=back: the outcome is read from the preferences and the param is stripped.
  await page.getByRole("link", { name: "AUTHORIZE ON GITHUB" }).click();
  await expect(page.getByRole("status").filter({ hasText: "GITHUB SAID YES · PRIVATE REPOS ARE ON" })).toBeVisible();
  await expect(page).toHaveURL(/\/settings\?mock=private_granted$/);
});

test("?mock=private_granted shows ON · ACTIVE, ?mock=private_reauth shows NEEDS GITHUB PERMISSION", async ({ page }) => {
  await page.goto("/settings?mock=private_granted");
  await expect(page.getByRole("switch", { name: "SHARE PRIVATE REPOS" })).toHaveAttribute("aria-checked", "true");
  await expect(page.getByText("ON · ACTIVE")).toBeVisible();
  await expect(page.getByRole("link", { name: "AUTHORIZE ON GITHUB" })).toHaveCount(0);

  await page.goto("/settings?mock=private_reauth");
  await expect(page.getByText("ON · NEEDS GITHUB PERMISSION")).toBeVisible();
  await expect(page.getByRole("link", { name: "AUTHORIZE ON GITHUB" })).toBeVisible();
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

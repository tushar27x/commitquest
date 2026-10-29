// Mock mode: every entry of hero-variants.json (?mock=<variant>) on Home, the Hero sheet and Change class.
import { expect, test } from "@playwright/test";
import { VARIANTS, fixtures, heroSummary, heroVariant } from "../../support/fixtures";

test("hero-variants.json still has every mood and tier the UI draws", () => {
  const heroes = [fixtures.hero, ...VARIANTS.map(heroVariant)];
  const moods = new Set(heroes.map((h) => h.mood));
  const tiers = new Set(heroes.map((h) => h.tier));
  expect([...moods].sort()).toEqual(["FAINTED", "HAPPY", "HUNGRY", "LEVEL_UP", "OKAY", "SLEEPING", "WEAK"]);
  expect([...tiers].sort()).toEqual(["CAMPFIRE", "CITADEL", "KEEP", "VILLAGE"]);
});

for (const variant of VARIANTS) {
  test(`?mock=${variant}: Home shows the hero's mood, hearts, streak and tier`, async ({ page }) => {
    const hero = heroVariant(variant);
    await page.goto(`/home?mock=${variant}`);
    await expect(page.getByText(heroSummary(hero))).toBeAttached();
    const scene = page.getByRole("region", { name: "Your hero" });
    await expect(scene.getByText(new RegExp(`^${hero.tier} · `))).toBeVisible();
  });
}

test("?mock=class_cooldown: Change class shows the cooldown and blocks the confirm button", async ({ page }) => {
  await page.goto("/hero/change?mock=class_cooldown");
  await expect(page.getByText(/CLASS COOLDOWN · /)).toBeVisible();
  await expect(page.getByRole("button", { name: /^NEXT CHANGE IN / })).toBeDisabled();
});

test("?mock=level_up: the top nav shows the new level", async ({ page }) => {
  const hero = heroVariant("level_up");
  expect(hero.level).not.toBe(fixtures.hero.level);
  await page.goto("/hero?mock=level_up");
  await expect(page.getByRole("navigation", { name: "Main" })).toContainText(`· LV ${hero.level}`);
});

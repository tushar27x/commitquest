// Mock mode: ?mock_error=<status> fails the first API call of the page load (a GET of the shell).
// Errors on SAVE, change class and settings need a per-endpoint stub: see specs/stubbed/.
import { expect, test } from "@playwright/test";
import { fixtures, heroSummary } from "../../support/fixtures";

test("401: SESSION ENDED over the screen, then back to the title", async ({ page }) => {
  await page.goto("/home?mock_error=401");
  const dialog = page.getByRole("dialog", { name: "SESSION ENDED" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "BACK TO TITLE" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("link", { name: "PRESS START" })).toBeVisible();
});

test("404: NEW GAME, and ROLL CHARACTER runs the first save", async ({ page }) => {
  await page.goto("/home?mock_error=404");
  await expect(page.getByText("NEW GAME")).toBeVisible();
  await page.getByRole("button", { name: "ROLL CHARACTER" }).click();
  await expect(page.getByText("SAVING YOUR QUEST…")).toBeVisible();
  await expect(page.getByText(heroSummary(fixtures.hero))).toBeAttached({ timeout: 10_000 });
  await expect(page.getByText("NEW GAME")).toBeHidden();
});

// A one-shot 5xx is retried once by the query client (lib/queries.ts) and never shows; the stubbed
// suite holds a 502 until RETRY. A 4xx isn't retried, so a 429 on a GET shows the card here.
test("429 on a GET: THE ROAD IS BLOCKED, and RETRY loads the screen", async ({ page }) => {
  await page.goto("/journal?mock_error=429");
  const card = page.getByRole("alert").filter({ hasText: "THE ROAD IS BLOCKED" });
  await expect(card).toBeVisible();
  await card.getByRole("button", { name: "RETRY" }).click();
  await expect(card).toBeHidden();
  await expect(page.getByRole("heading", { name: "YEAR IN REVIEW" })).toBeVisible();
});

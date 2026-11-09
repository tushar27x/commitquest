// Mock mode: the SHARE PRIVATE REPOS warning flow and the lock (contract B11, "Settings copy for the private switch").
// Mock mode answers inside the page, so "no PUT" is checked on the screen here; the wire is checked in
// specs/stubbed/private.spec.ts. The story clock is 2026-10-16, so a change made now locks until 23 Oct 2026.
import { expect, test, type Page } from "@playwright/test";

const priv = (page: Page) => page.getByRole("switch", { name: "SHARE PRIVATE REPOS" });
const help = (page: Page) => page.locator("#private-help");
const tick = (page: Page) => page.getByRole("checkbox", { name: "I understand" });
const confirm = (page: Page) => page.getByRole("button", { name: "CONFIRM" });

test.describe("ON warning (private_off_granted: GitHub already shares the scope)", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/settings?mock=private_off_granted");
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
  });

  test("the switch opens the dialog; CONFIRM is disabled until the I understand tick", async ({ page }) => {
    await priv(page).click();
    const dialog = page.getByRole("dialog", { name: "SHARE PRIVATE REPOS?" });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText("read AND write access");
    await expect(dialog).toContainText("can't change it again for 7 days");
    await expect(confirm(page)).toBeDisabled();
    await tick(page).check();
    await expect(confirm(page)).toBeEnabled();
    await tick(page).uncheck();
    await expect(confirm(page)).toBeDisabled();
    // Nothing happened to the switch behind the dialog.
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
  });

  test("CANCEL leaves the switch unchanged and nothing is resynced", async ({ page }) => {
    await priv(page).click();
    await tick(page).check();
    await page.getByRole("button", { name: "CANCEL" }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await expect(priv(page)).toBeEnabled();
    await expect(page.getByText("RESYNCING YOUR INSIGHTS…")).toHaveCount(0);
    await expect(page.locator("#private-lock")).toHaveCount(0);
    // The tick does not survive: the next dialog starts clean.
    await priv(page).click();
    await expect(tick(page)).not.toBeChecked();
    await expect(confirm(page)).toBeDisabled();
  });

  test("Esc leaves the switch unchanged and nothing is resynced", async ({ page }) => {
    await priv(page).click();
    await tick(page).check();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await expect(priv(page)).toBeEnabled();
    await expect(page.getByText("RESYNCING YOUR INSIGHTS…")).toHaveCount(0);
  });

  test("CONFIRM resyncs for about 3 s with the switch disabled, then ends ON and locked with a date", async ({ page }) => {
    await priv(page).click();
    await tick(page).check();
    await confirm(page).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(help(page)).toHaveText("RESYNCING YOUR INSIGHTS…");
    await expect(priv(page)).toBeDisabled();
    await expect(page.getByRole("status").filter({ hasText: "Don't close this tab" })).toBeVisible();
    // The mock sync takes 3 s (SYNC_DELAY_MS).
    await expect(page.getByRole("status").filter({ hasText: "PRIVATE REPOS ARE ON · INSIGHTS RESYNCED" })).toBeVisible({ timeout: 8_000 });
    await expect(priv(page)).toHaveAttribute("aria-checked", "true");
    await expect(priv(page)).toBeDisabled();
    await expect(help(page)).toHaveText("LOCKED UNTIL 23 OCT 2026");
    await expect(page.locator("#private-lock")).toContainText("23 Oct 2026");
  });
});

test.describe("OFF warning (private_granted)", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/settings?mock=private_granted");
    await expect(priv(page)).toHaveAttribute("aria-checked", "true");
  });

  test("turning off asks first, with the OFF copy; CONFIRM needs the tick; CANCEL keeps it on", async ({ page }) => {
    await priv(page).click();
    const dialog = page.getByRole("dialog", { name: "STOP SHARING PRIVATE REPOS?" });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText("can't turn it back on for 7 days");
    await expect(dialog).toContainText("not rewound");
    await expect(confirm(page)).toBeDisabled();
    await page.getByRole("button", { name: "CANCEL" }).click();
    await expect(priv(page)).toHaveAttribute("aria-checked", "true");
    await expect(help(page)).toHaveText("ON · ACTIVE");
  });

  test("CONFIRM removes private work with a resync, ends OFF and locked", async ({ page }) => {
    await priv(page).click();
    await tick(page).check();
    await confirm(page).click();
    await expect(help(page)).toHaveText("RESYNCING YOUR INSIGHTS…");
    await expect(priv(page)).toBeDisabled();
    await expect(page.getByRole("status").filter({ hasText: "PRIVATE REPOS ARE OFF · INSIGHTS RESYNCED" })).toBeVisible({ timeout: 8_000 });
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
    await expect(help(page)).toHaveText("LOCKED UNTIL 23 OCT 2026");
  });
});

test("turning off while waiting for GitHub (true/none) needs no dialog and no lock", async ({ page }) => {
  await page.goto("/settings?mock=private_reauth");
  await expect(priv(page)).toHaveAttribute("aria-checked", "true");
  await expect(priv(page)).toBeEnabled();
  await priv(page).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("status").filter({ hasText: "SAVED" })).toBeVisible();
  await expect(priv(page)).toHaveAttribute("aria-checked", "false");
  await expect(page.getByText("RESYNCING YOUR INSIGHTS…")).toHaveCount(0);
  await expect(page.locator("#private-lock")).toHaveCount(0);
});

test.describe("the lock variants", () => {
  for (const [variant, checked] of [
    ["private_locked_on", "true"],
    ["private_locked_off", "false"],
  ] as const) {
    test(`?mock=${variant}: the switch is disabled and the date is shown`, async ({ page }) => {
      await page.goto(`/settings?mock=${variant}`);
      await expect(priv(page)).toHaveAttribute("aria-checked", checked);
      await expect(priv(page)).toBeDisabled();
      await expect(help(page)).toHaveText("LOCKED UNTIL 23 OCT 2026");
      await expect(page.locator("#private-lock")).toContainText("(until 23 Oct 2026)");
      // The date is the switch's description for assistive tech.
      await expect(priv(page)).toHaveAttribute("aria-describedby", /private-lock/);
      // A click on a disabled switch opens nothing.
      await priv(page).click({ force: true });
      await expect(page.getByRole("dialog")).toHaveCount(0);
    });
  }

  test("?mock=private_lock_lost_scope: waiting while locked; AUTHORIZE is disabled but turning off is allowed", async ({ page }) => {
    await page.goto("/settings?mock=private_lock_lost_scope");
    await expect(priv(page)).toHaveAttribute("aria-checked", "true");
    await expect(priv(page)).toBeEnabled();
    await expect(page.locator("#private-lock")).toContainText("Locked until 23 Oct 2026");
    await expect(page.locator("#private-lock")).toContainText("You can still turn this off");
    await expect(page.getByRole("button", { name: "AUTHORIZE ON GITHUB" })).toBeDisabled();
    await expect(page.getByRole("link", { name: "AUTHORIZE ON GITHUB" })).toHaveCount(0);
    await priv(page).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(priv(page)).toHaveAttribute("aria-checked", "false");
  });
});

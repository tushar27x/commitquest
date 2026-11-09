// Installable web app: the manifest, its icons and the head tags that Android, desktop Chrome and iOS read.
import { expect, test } from "@playwright/test";

test("the manifest is served and every icon in it loads", async ({ page, request }) => {
  await page.goto("/home");
  const href = await page.locator('link[rel="manifest"]').getAttribute("href");
  expect(href).toBe("/manifest.webmanifest");

  const res = await request.get(href!);
  expect(res.ok()).toBe(true);
  const manifest = await res.json();
  expect(manifest).toMatchObject({
    name: "Insight Hub",
    start_url: "/home",
    display: "standalone",
    theme_color: "#13111C",
    background_color: "#13111C",
  });

  const purposes = manifest.icons.map((icon: { sizes: string; purpose: string }) => `${icon.sizes}:${icon.purpose}`);
  expect(purposes).toEqual(expect.arrayContaining(["192x192:any", "512x512:any", "512x512:maskable"]));
  for (const icon of manifest.icons as { src: string; type: string }[]) {
    const iconRes = await request.get(icon.src);
    expect(iconRes.ok(), icon.src).toBe(true);
    expect(iconRes.headers()["content-type"]).toBe(icon.type);
  }
});

test("the page head has the iOS home-screen icon and title, and the favicons", async ({ page, request }) => {
  await page.goto("/");
  const apple = page.locator('link[rel="apple-touch-icon"]');
  await expect(apple).toHaveAttribute("href", "/icons/apple-touch-icon.png");
  expect((await request.get("/icons/apple-touch-icon.png")).ok()).toBe(true);
  await expect(page.locator('meta[name="apple-mobile-web-app-title"]')).toHaveAttribute("content", "Insight Hub");
  await expect(page.locator('link[rel="icon"]')).not.toHaveCount(0);
});

// Settings -> INSTALL APP. Chromium never offers an install in a test, so the browser's event is faked.
test.describe("INSTALL APP in Settings", () => {
  test("shows nothing when the browser can't install the app", async ({ page }) => {
    await page.goto("/settings");
    await expect(page.getByRole("switch", { name: "SHOW ME IN THE ARENA" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "INSTALL APP" })).toHaveCount(0);
  });

  test("a browser install prompt becomes an INSTALL button that opens it once", async ({ page }) => {
    await page.goto("/home"); // the listener starts with the app, before Settings is opened
    await page.evaluate(() => {
      const event = new Event("beforeinstallprompt", { cancelable: true }) as Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> };
      (window as unknown as { prompts: number }).prompts = 0;
      event.prompt = async () => {
        (window as unknown as { prompts: number }).prompts += 1;
      };
      event.userChoice = Promise.resolve({ outcome: "accepted" });
      window.dispatchEvent(event);
    });
    await page.getByRole("link", { name: "SETTINGS" }).first().click();

    await expect(page.getByRole("heading", { name: "INSTALL APP" })).toBeVisible();
    await page.getByRole("button", { name: "INSTALL", exact: true }).click();
    await expect.poll(() => page.evaluate(() => (window as unknown as { prompts: number }).prompts)).toBe(1);
    // The browser's event works once: the section goes away after it.
    await expect(page.getByRole("heading", { name: "INSTALL APP" })).toHaveCount(0);
  });

  test.describe("on an iPhone", () => {
    test.use({
      userAgent:
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
    });

    test("shows the Share-menu steps instead of a button", async ({ page }) => {
      await page.goto("/settings");
      await expect(page.getByRole("heading", { name: "INSTALL APP" })).toBeVisible();
      await expect(page.getByText("Add to Home Screen")).toBeVisible();
      await expect(page.getByRole("button", { name: "INSTALL", exact: true })).toHaveCount(0);
    });
  });
});

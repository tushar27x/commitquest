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

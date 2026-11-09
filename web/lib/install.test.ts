import { describe, expect, it } from "vitest";
import { isIos, resolveInstallMode } from "./install";

describe("resolveInstallMode", () => {
  it("shows the button while a browser prompt is waiting", () => {
    expect(resolveInstallMode({ canPrompt: true, installed: false, ios: false })).toBe("prompt");
  });

  it("shows the steps on iOS, where there is no prompt", () => {
    expect(resolveInstallMode({ canPrompt: false, installed: false, ios: true })).toBe("ios");
  });

  it("shows nothing in other browsers without a prompt", () => {
    expect(resolveInstallMode({ canPrompt: false, installed: false, ios: false })).toBe("none");
  });

  it("shows nothing once the app is installed, whatever else is true", () => {
    expect(resolveInstallMode({ canPrompt: true, installed: true, ios: false })).toBe("none");
    expect(resolveInstallMode({ canPrompt: false, installed: true, ios: true })).toBe("none");
  });
});

describe("isIos", () => {
  const iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
  const mac = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15";

  it("knows iPhones", () => expect(isIos(iphone, "iPhone", 5)).toBe(true));
  it("knows an iPad that calls itself a Mac (touch screen)", () => expect(isIos(mac, "MacIntel", 5)).toBe(true));
  it("does not take a real Mac for an iPad", () => expect(isIos(mac, "MacIntel", 0)).toBe(false));
  it("does not take Android for iOS", () => expect(isIos("Mozilla/5.0 (Linux; Android 14; Pixel 8)", "Linux armv8l", 5)).toBe(false));
});

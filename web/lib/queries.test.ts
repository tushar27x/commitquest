import { afterEach, describe, expect, it, vi } from "vitest";
import type { Preferences } from "./api/types";
import { afterPreferencesSaved, makeQueryClient, queryKeys, sendBrowserZone } from "./queries";

const stored: Preferences = { timezone: "UTC", rest_days: ["SUNDAY"], alerts_enabled: true, public_profile: false, include_private: false, private_access: "none", private_locked_until: null };

function stubBackend(prefs: Preferences, putStatus = 200) {
  const puts: unknown[] = [];
  vi.stubGlobal("fetch", async (_url: string, init: RequestInit) => {
    if (init.method === "PUT") {
      puts.push(JSON.parse(String(init.body)));
      return putStatus === 200
        ? Response.json(puts.at(-1))
        : new Response(JSON.stringify({ status: putStatus }), { status: putStatus });
    }
    return Response.json(prefs);
  });
  return puts;
}

/** The browser's zone; only the first lookup, so `sameTimeZone` still resolves real zones. */
function inZone(zone: string) {
  vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions").mockReturnValueOnce({
    ...new Intl.DateTimeFormat().resolvedOptions(),
    timeZone: zone,
  });
}

describe("afterPreferencesSaved", () => {
  it("reloads the hero, every leaderboard page and the rank, and stores the saved preferences", () => {
    const client = makeQueryClient();
    client.setQueryData(queryKeys.leaderboard("MONK", 0), { entries: [] });
    client.setQueryData(queryKeys.leaderboard("ROGUE", 2), { entries: [] });
    client.setQueryData(queryKeys.rank, { rank: 1 });
    client.setQueryData(queryKeys.insights, { user_name: "x" });
    const saved = { ...stored, public_profile: true };

    afterPreferencesSaved(client, saved);

    expect(client.getQueryData(queryKeys.preferences)).toEqual(saved);
    expect(client.getQueryState(queryKeys.leaderboard("MONK", 0))?.isInvalidated).toBe(true);
    expect(client.getQueryState(queryKeys.leaderboard("ROGUE", 2))?.isInvalidated).toBe(true);
    expect(client.getQueryState(queryKeys.rank)?.isInvalidated).toBe(true);
    expect(client.getQueryState(queryKeys.insights)?.isInvalidated).toBe(false);
  });
});

describe("afterPreferencesSaved: an effective private flip (B11)", () => {
  const granted: Preferences = { ...stored, private_access: "granted" };

  function seeded(before: Preferences) {
    const client = makeQueryClient();
    client.setQueryData(queryKeys.preferences, before);
    client.setQueryData(queryKeys.insights, { user_name: "x" });
    client.setQueryData(queryKeys.hero, { level: 1 });
    client.setQueryData(queryKeys.heroEvents(30), { events: [] });
    client.setQueryData(queryKeys.rank, { rank: 1 });
    client.setQueryData(queryKeys.leaderboard("MONK", 0), { entries: [] });
    return client;
  }
  const stale = (client: ReturnType<typeof makeQueryClient>, key: readonly unknown[]) => client.getQueryState(key)?.isInvalidated;

  it("reloads insights, hero, journal events and the Arena", () => {
    const client = seeded(granted);
    afterPreferencesSaved(client, { ...granted, include_private: true, private_locked_until: "2026-10-23T09:30:00Z" });
    for (const key of [queryKeys.insights, queryKeys.hero, queryKeys.heroEvents(30), queryKeys.rank, queryKeys.leaderboard("MONK", 0)]) {
      expect(stale(client, key)).toBe(true);
    }
  });

  it("leaves the insights alone when only the wish was recorded (no scope yet) or nothing flipped", () => {
    const wish = seeded(stored);
    afterPreferencesSaved(wish, { ...stored, include_private: true });
    expect(stale(wish, queryKeys.insights)).toBe(false);
    expect(stale(wish, queryKeys.heroEvents(30))).toBe(false);

    const same = seeded(granted);
    afterPreferencesSaved(same, { ...granted, alerts_enabled: false });
    expect(stale(same, queryKeys.insights)).toBe(false);
  });
});

describe("sendBrowserZone", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("sends the full preferences with only the browser's zone changed, then reloads the hero", async () => {
    const puts = stubBackend(stored);
    inZone("Europe/Berlin");
    const client = makeQueryClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");

    await sendBrowserZone(client);

    expect(puts).toEqual([{ ...stored, timezone: "Europe/Berlin" }]);
    expect(client.getQueryData(queryKeys.preferences)).toEqual({ ...stored, timezone: "Europe/Berlin" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.hero, exact: true });
  });

  it("never touches a zone the user picked", async () => {
    const puts = stubBackend({ ...stored, timezone: "Asia/Kolkata" });
    inZone("Europe/Berlin");
    await sendBrowserZone(makeQueryClient());
    expect(puts).toEqual([]);
  });

  it("ignores a failed PUT", async () => {
    stubBackend(stored, 502);
    inZone("Europe/Berlin");
    await expect(sendBrowserZone(makeQueryClient())).resolves.toBeUndefined();
  });
});

import { afterEach, describe, expect, it, vi } from "vitest";
import type { Preferences } from "./api/types";
import { makeQueryClient, queryKeys, sendBrowserZone } from "./queries";

const stored: Preferences = { timezone: "UTC", rest_days: ["SUNDAY"], alerts_enabled: true, public_profile: false };

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

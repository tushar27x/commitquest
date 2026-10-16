import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, NETWORK_ERROR, api, parseRetryAfter } from "./api";
import { shouldRetry } from "./queries";

afterEach(() => vi.unstubAllGlobals());

describe("parseRetryAfter", () => {
  it("reads delay-seconds", () => {
    expect(parseRetryAfter("451")).toBe(451);
  });

  it("reads an HTTP date, rounded up and at least 1", () => {
    const now = Date.UTC(2026, 9, 16, 12, 0, 0);
    expect(parseRetryAfter("Fri, 16 Oct 2026 12:00:01 GMT", now)).toBe(1);
    expect(parseRetryAfter("Fri, 16 Oct 2026 11:00:00 GMT", now)).toBe(1);
  });

  it("returns null when missing or unreadable", () => {
    expect(parseRetryAfter(null)).toBeNull();
    expect(parseRetryAfter("soon")).toBeNull();
  });
});

describe("api", () => {
  it("throws a typed ApiError with retryAfter on 429", async () => {
    const problem = { title: "Too Many Requests", status: 429, detail: "Sync cooldown" };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(problem), { status: 429, headers: { "Retry-After": "120" } })),
    );
    const err = await api("/api/user/insights").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 429, retryAfter: 120, message: "Sync cooldown", problem });
  });

  it("maps a failed fetch to a network ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("Failed to fetch"))));
    const err = await api("/api/user/insights").catch((e: unknown) => e);
    expect(err).toMatchObject({ status: NETWORK_ERROR, isNetwork: true });
  });

  it("sends JSON bodies and returns parsed JSON", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(api("/api/user/sync", { method: "POST", body: { a: 1 } })).resolves.toEqual({ ok: true });
    const init = fetchMock.mock.calls[0][1];
    expect(init?.body).toBe('{"a":1}');
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });
});

describe("shouldRetry", () => {
  it("never retries 4xx", () => {
    for (const status of [401, 404, 409, 429]) expect(shouldRetry(0, new ApiError(status, "x"))).toBe(false);
  });

  it("retries 5xx and network errors once", () => {
    expect(shouldRetry(0, new ApiError(502, "x"))).toBe(true);
    expect(shouldRetry(0, new ApiError(NETWORK_ERROR, "x"))).toBe(true);
    expect(shouldRetry(1, new ApiError(502, "x"))).toBe(false);
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearToken, decodeJwt, getToken, hasSession, isExpired, setToken, tokenFromFragment } from "./auth";

// Built at runtime: no token-shaped literals in tests (B31).
const b64url = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
const jwt = (claims: object) => [b64url({ alg: "HS256" }), b64url(claims), "signature"].join(".");

const NOW = Date.UTC(2026, 9, 16, 12, 0, 0);
const inSeconds = (s: number) => Math.floor(NOW / 1000) + s;

function fakeStorage(throws = false): Storage {
  const data = new Map<string, string>();
  const guard = () => {
    if (throws) throw new DOMException("blocked", "SecurityError");
  };
  return {
    get length() {
      return data.size;
    },
    clear: () => data.clear(),
    key: (i) => [...data.keys()][i] ?? null,
    getItem: (k) => (guard(), data.get(k) ?? null),
    setItem: (k, v) => (guard(), void data.set(k, v)),
    removeItem: (k) => (guard(), void data.delete(k)),
  };
}

describe("decodeJwt", () => {
  it("reads the payload claims", () => {
    expect(decodeJwt(jwt({ sub: "42", name: "octo", exp: 100 }))).toEqual({ sub: "42", name: "octo", exp: 100 });
  });

  it("returns null for garbage", () => {
    expect(decodeJwt("not-a-jwt")).toBeNull();
    expect(decodeJwt("a.%%%.c")).toBeNull();
  });
});

describe("isExpired", () => {
  it("is false well before exp", () => {
    expect(isExpired(jwt({ exp: inSeconds(3600) }), NOW)).toBe(false);
  });

  it("is true within the 30 s skew and after exp", () => {
    expect(isExpired(jwt({ exp: inSeconds(10) }), NOW)).toBe(true);
    expect(isExpired(jwt({ exp: inSeconds(-1) }), NOW)).toBe(true);
  });

  it("is true without exp or for an unreadable token", () => {
    expect(isExpired(jwt({ sub: "42" }), NOW)).toBe(true);
    expect(isExpired("nope", NOW)).toBe(true);
  });
});

describe("token storage", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("stores, returns and clears a valid token", () => {
    vi.stubGlobal("window", { localStorage: fakeStorage() });
    const token = jwt({ exp: inSeconds(3600) });
    expect(setToken(token)).toBe(true);
    expect(getToken()).toBe(token);
    clearToken();
    expect(getToken()).toBeNull();
  });

  it("drops an expired token on read", () => {
    const storage = fakeStorage();
    vi.stubGlobal("window", { localStorage: storage });
    setToken(jwt({ exp: inSeconds(-60) }));
    expect(getToken()).toBeNull();
    expect(storage.length).toBe(0);
  });

  it("keeps the token in memory when storage throws", () => {
    vi.stubGlobal("window", { localStorage: fakeStorage(true) });
    const token = jwt({ exp: inSeconds(3600) });
    expect(setToken(token)).toBe(false);
    expect(getToken()).toBe(token);
    expect(hasSession()).toBe(true);
    expect(() => clearToken()).not.toThrow();
    expect(getToken()).toBeNull();
    expect(hasSession()).toBe(false);
  });

  it("hasSession is false for an expired token and leaves it in place", () => {
    const storage = fakeStorage();
    vi.stubGlobal("window", { localStorage: storage });
    setToken(jwt({ exp: inSeconds(-60) }));
    expect(hasSession()).toBe(false);
    expect(storage.length).toBe(1);
  });
});

describe("tokenFromFragment", () => {
  it("reads #token=", () => {
    expect(tokenFromFragment("#token=abc.def.ghi")).toBe("abc.def.ghi");
    expect(tokenFromFragment("token=abc")).toBe("abc");
  });

  it("returns null without a token", () => {
    expect(tokenFromFragment("")).toBeNull();
    expect(tokenFromFragment("#token=")).toBeNull();
    expect(tokenFromFragment("#other=1")).toBeNull();
  });
});

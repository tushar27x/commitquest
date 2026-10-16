// The backend JWT (HS256, 24 h, `sub` = GitHub id) lives in localStorage.
// Storage can throw (private mode, blocked site data), so every access is guarded,
// and the token falls back to memory for this page load.

import { MOCK_MODE } from "./mock";

const TOKEN_KEY = "ih.token";
/** Treat a token as expired slightly early, so a request never leaves with a dying token. */
const EXPIRY_SKEW_MS = 30_000;

let memoryToken: string | null = null;
const listeners = new Set<() => void>();

export interface JwtClaims {
  sub?: string;
  name?: string;
  exp?: number;
  iat?: number;
  iss?: string;
}

/** Decodes the payload without verifying it: only the backend can verify the signature. */
export function decodeJwt(token: string): JwtClaims | null {
  const part = token.split(".")[1];
  if (!part) return null;
  try {
    const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const claims: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return claims && typeof claims === "object" ? (claims as JwtClaims) : null;
  } catch {
    return null;
  }
}

/** True when the token is unreadable, has no `exp`, or expires within the skew. */
export function isExpired(token: string, now: number = Date.now()): boolean {
  const exp = decodeJwt(token)?.exp;
  if (typeof exp !== "number") return true;
  return exp * 1000 <= now + EXPIRY_SKEW_MS;
}

function readToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY) ?? memoryToken;
  } catch {
    return memoryToken;
  }
}

function notify() {
  listeners.forEach((listener) => listener());
}

/** The stored token, or null if there is none or it has expired (an expired one is removed). */
export function getToken(): string | null {
  const token = readToken();
  if (token && isExpired(token)) {
    clearToken();
    return null;
  }
  return token;
}

/** Returns false when the browser refused to persist it (it still works for this page load). */
export function setToken(token: string): boolean {
  memoryToken = token;
  let stored = true;
  try {
    window.localStorage.setItem(TOKEN_KEY, token);
  } catch {
    stored = false;
  }
  notify();
  return stored;
}

export function clearToken(): void {
  memoryToken = null;
  try {
    window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing stored, nothing to clear.
  }
  notify();
}

/** Whether there is a usable session. Pure (no clean-up), so it is safe as a render snapshot. */
export function hasSession(): boolean {
  if (MOCK_MODE) return true;
  const token = readToken();
  return token !== null && !isExpired(token);
}

/** Calls `listener` when the token changes, in this tab or another one. */
export function subscribeToken(listener: () => void): () => void {
  listeners.add(listener);
  const onStorage = (e: StorageEvent) => {
    if (e.key === null || e.key === TOKEN_KEY) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

/** Reads `token` from a URL fragment such as `#token=…` (the backend's login hand-off). */
export function tokenFromFragment(hash: string): string | null {
  const params = new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash);
  const token = params.get("token");
  return token ? token : null;
}

/**
 * Where PRESS START and RECONNECT GITHUB go: straight to the backend, because the OAuth
 * state cookie must live on the backend origin. Mock mode has no backend, so it goes home.
 */
export function loginUrl(): string {
  if (MOCK_MODE) return "/home";
  const backend = (process.env.NEXT_PUBLIC_BACKEND_URL ?? "").replace(/\/+$/, "");
  return `${backend}/api/auth/login`;
}

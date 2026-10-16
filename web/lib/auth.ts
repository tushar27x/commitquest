// The backend JWT (HS256, 24 h, `sub` = GitHub id) lives in localStorage.
// Storage can throw (private mode, blocked site data), so every access is guarded:
// without storage the user just has to log in again.

const TOKEN_KEY = "ih.token";
/** Treat a token as expired slightly early, so a request never leaves with a dying token. */
const EXPIRY_SKEW_MS = 30_000;

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

/** The stored token, or null if there is none or it has expired (an expired one is removed). */
export function getToken(): string | null {
  let token: string | null;
  try {
    token = window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
  if (token && isExpired(token)) {
    clearToken();
    return null;
  }
  return token;
}

/** Returns false when the browser refused to store it. */
export function setToken(token: string): boolean {
  try {
    window.localStorage.setItem(TOKEN_KEY, token);
    return true;
  } catch {
    return false;
  }
}

export function clearToken(): void {
  try {
    window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing stored, nothing to clear.
  }
}

/** Reads `token` from a URL fragment such as `#token=…` (the backend's login hand-off). */
export function tokenFromFragment(hash: string): string | null {
  const params = new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash);
  const token = params.get("token");
  return token ? token : null;
}

// A small `fetch` wrapper for the backend API. Every failure becomes an `ApiError`, so the
// system states (plan §4) can switch on `status` and count down from `retryAfter`.

import type { ProblemDetail } from "./api/types";
import { getToken } from "./auth";
import { mockFetch, servedByMock } from "./mock";

/** `status` for a request that never got an HTTP response (offline, DNS, CORS, abort). */
export const NETWORK_ERROR = 0;

export class ApiError extends Error {
  readonly status: number;
  /** Seconds from `Retry-After` (429), rounded up and at least 1; otherwise null. */
  readonly retryAfter: number | null;
  readonly problem: ProblemDetail | null;

  constructor(status: number, message: string, retryAfter: number | null = null, problem: ProblemDetail | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.retryAfter = retryAfter;
    this.problem = problem;
  }

  get isNetwork(): boolean {
    return this.status === NETWORK_ERROR;
  }

  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500;
  }
}

/** `Retry-After` is either delay-seconds or an HTTP date. */
export function parseRetryAfter(value: string | null, now: number = Date.now()): number | null {
  if (!value) return null;
  const trimmed = value.trim();
  const ms = /^\d+$/.test(trimmed) ? Number(trimmed) * 1000 : Date.parse(trimmed) - now;
  if (Number.isNaN(ms)) return null;
  return Math.max(1, Math.ceil(ms / 1000));
}

async function readProblem(res: Response): Promise<ProblemDetail | null> {
  try {
    const body: unknown = await res.json();
    return body && typeof body === "object" ? (body as ProblemDetail) : null;
  } catch {
    return null;
  }
}

export interface ApiOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
}

/** Calls `/api/...` on this origin (Next rewrites forward it to the backend). */
export async function api<T>(path: string, { method = "GET", body, signal }: ApiOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const init: RequestInit = {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  };

  let res: Response;
  try {
    res = await (servedByMock(path) ? mockFetch(path, init) : fetch(path, init));
  } catch (err) {
    if (signal?.aborted) throw err;
    throw new ApiError(NETWORK_ERROR, `${method} ${path} failed: network error`);
  }

  if (!res.ok) {
    const problem = await readProblem(res);
    // A 401 doesn't clear the token here: the game shell shows "Session ended" first
    // and clears it then (components/game/GameState.tsx).
    throw new ApiError(
      res.status,
      problem?.detail ?? problem?.title ?? `${method} ${path} failed with ${res.status}`,
      parseRetryAfter(res.headers.get("Retry-After")),
      problem,
    );
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

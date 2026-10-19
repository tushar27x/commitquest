"use client";

// Shared game state for the tab screens: SAVE (POST /sync) and the system states of plan §4.
//   Saving        - sync in flight            -> full-screen overlay
//   Resting       - 429 + Retry-After         -> SAVE and ROLL CHARACTER count down
//   Reconnect     - 409 on sync               -> banner, data stays
//   Session ended - 401 on any call           -> dialog over the cached data, then the title
//   New game      - 404 on sync (rare)        -> same as 404 on insights
//   Road blocked  - 500 / 502 / network       -> inline card with RETRY
// Branch on status (and endpoint) only: the problem `type` is always about:blank.

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api";
import { clearToken } from "@/lib/auth";
import { useSync } from "@/lib/queries";

const REST_KEY = "ih.restUntil";
/** If a 429 comes without Retry-After, assume the 10-minute sync cooldown. */
const DEFAULT_REST_S = 600;

export interface GameState {
  save: () => void;
  saving: boolean;
  /** Seconds left of the sync cooldown; 0 when SAVE is available. */
  restSeconds: number;
  needsReauth: boolean;
  syncBlocked: boolean;
  /** POST /sync said 404: show NEW GAME like a 404 on insights. */
  syncNotFound: boolean;
  sessionEnded: boolean;
  /** Leaves after "Session ended": drops the cached data and the flag. */
  endSession: () => void;
}

const GameStateContext = createContext<GameState | null>(null);

export function useGameState(): GameState {
  const state = useContext(GameStateContext);
  if (!state) throw new Error("useGameState must be used inside <GameStateProvider>");
  return state;
}

function readRestUntil(): number {
  try {
    const value = Number(window.localStorage.getItem(REST_KEY));
    return Number.isFinite(value) ? value : 0;
  } catch {
    return 0;
  }
}

function writeRestUntil(value: number) {
  try {
    if (value > 0) window.localStorage.setItem(REST_KEY, String(value));
    else window.localStorage.removeItem(REST_KEY);
  } catch {
    // The countdown still works for this page load.
  }
}

const isUnauthorized = (error: unknown) => error instanceof ApiError && error.status === 401;

/** Client-only: GameShell mounts it once the session is known, which never happens on the server. */
export function GameStateProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const sync = useSync();

  // The cooldown survives reloads: a failed login sync also uses it up.
  const [restUntil, setRestUntil] = useState(readRestUntil);
  const [now, setNow] = useState(() => Date.now());
  const [needsReauth, setNeedsReauth] = useState(false);
  const [syncBlocked, setSyncBlocked] = useState(false);
  const [syncNotFound, setSyncNotFound] = useState(false);
  const [sessionEnded, setSessionEnded] = useState(false);

  const restSeconds = Math.max(0, Math.ceil((restUntil - now) / 1000));

  useEffect(() => {
    if (restUntil <= Date.now()) return;
    const timer = window.setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= restUntil) {
        writeRestUntil(0);
        window.clearInterval(timer);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [restUntil]);

  // A 401 from any query or mutation (SAVE, change class, settings) ends the session.
  useEffect(() => {
    const onEvent = (event: { type: string; action?: { type: string; error?: unknown } }) => {
      if (event.type === "updated" && event.action?.type === "error" && isUnauthorized(event.action.error)) {
        setSessionEnded(true);
      }
    };
    const unsubscribeQueries = queryClient.getQueryCache().subscribe(onEvent);
    const unsubscribeMutations = queryClient.getMutationCache().subscribe(onEvent);
    return () => {
      unsubscribeQueries();
      unsubscribeMutations();
    };
  }, [queryClient]);

  // The token is dead: drop it, but keep the cached data on screen behind the dialog.
  useEffect(() => {
    if (sessionEnded) clearToken();
  }, [sessionEnded]);

  const { mutate, isPending: saving } = sync;

  const save = useCallback(() => {
    if (saving || restSeconds > 0) return;
    mutate(undefined, {
      onSuccess: () => {
        setNeedsReauth(false);
        setSyncBlocked(false);
        setSyncNotFound(false);
      },
      onError: (error) => {
        if (error.status !== 404) setSyncNotFound(false);
        switch (error.status) {
          case 429: {
            const t = Date.now();
            const until = t + (error.retryAfter ?? DEFAULT_REST_S) * 1000;
            writeRestUntil(until);
            setNow(t);
            setRestUntil(until);
            setSyncBlocked(false);
            break;
          }
          case 409:
            setNeedsReauth(true);
            setSyncBlocked(false);
            break;
          case 401:
            setSessionEnded(true);
            break;
          case 404:
            setSyncNotFound(true);
            setSyncBlocked(false);
            break;
          default:
            setSyncBlocked(true);
        }
      },
    });
  }, [mutate, saving, restSeconds]);

  const endSession = useCallback(() => {
    clearToken();
    queryClient.clear();
    setSessionEnded(false);
    setNeedsReauth(false);
    setSyncBlocked(false);
    setSyncNotFound(false);
  }, [queryClient]);

  const value = useMemo<GameState>(
    () => ({ save, saving, restSeconds, needsReauth, syncBlocked, syncNotFound, sessionEnded, endSession }),
    [save, saving, restSeconds, needsReauth, syncBlocked, syncNotFound, sessionEnded, endSession],
  );

  return <GameStateContext.Provider value={value}>{children}</GameStateContext.Provider>;
}

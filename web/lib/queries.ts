// TanStack Query hooks for every backend read, plus SAVE (`POST /sync`).
// Defaults (plan §4): no retry on 4xx, a long staleTime, no refetch on window focus,
// because `GET /insights` is limited to 20/min per user.

import { QueryClient, keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./api";
import type { ChangeClassRequest, Hero, HeroEvents, Archetype, Insights, Leaderboard, Preferences, Rank } from "./api/types";
import { adoptBrowserZone, browserTimeZone } from "./preferences";

const STALE_TIME_MS = 10 * 60_000;
const GC_TIME_MS = 30 * 60_000;
/** One retry for 5xx and network errors; 4xx (401, 404, 409, 429…) never retries. */
const MAX_RETRIES = 1;

export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.isClientError) return false;
  return failureCount < MAX_RETRIES;
}

export function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: STALE_TIME_MS,
        gcTime: GC_TIME_MS,
        refetchOnWindowFocus: false,
        retry: shouldRetry,
      },
      mutations: { retry: false },
    },
  });
}

export const queryKeys = {
  insights: ["insights"] as const,
  hero: ["hero"] as const,
  heroEvents: (days: number) => ["hero", "events", days] as const,
  preferences: ["preferences"] as const,
  leaderboard: (archetype: Archetype, page: number) => ["leaderboard", archetype, page] as const,
  rank: ["rank"] as const,
};

export function useInsights() {
  return useQuery<Insights, ApiError>({
    queryKey: queryKeys.insights,
    queryFn: ({ signal }) => api<Insights>("/api/user/insights", { signal }),
  });
}

export function useHero() {
  return useQuery<Hero, ApiError>({
    queryKey: queryKeys.hero,
    queryFn: ({ signal }) => api<Hero>("/api/user/hero", { signal }),
  });
}

/** `days` is 1–90 (contract); the backend defaults to 30. */
export function useHeroEvents(days = 30) {
  return useQuery<HeroEvents, ApiError>({
    queryKey: queryKeys.heroEvents(days),
    queryFn: ({ signal }) => api<HeroEvents>(`/api/user/hero/events?days=${days}`, { signal }),
  });
}

export function usePreferences() {
  return useQuery<Preferences, ApiError>({
    queryKey: queryKeys.preferences,
    queryFn: ({ signal }) => api<Preferences>("/api/user/preferences", { signal }),
  });
}

/** One page of a class's board (20 rows). The previous page stays on screen while the next loads. */
export function useLeaderboard(archetype: Archetype, page: number) {
  return useQuery<Leaderboard, ApiError>({
    queryKey: queryKeys.leaderboard(archetype, page),
    queryFn: ({ signal }) => api<Leaderboard>(`/api/leaderboard?archetype=${archetype}&page=${page}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

/** The caller's place on their current class's board. 404 "Not in the Arena" means they aren't ranked. */
export function useRank() {
  return useQuery<Rank, ApiError>({
    queryKey: queryKeys.rank,
    queryFn: ({ signal }) => api<Rank>("/api/user/rank", { signal }),
  });
}

/** SAVE: a synchronous sync (usually < 30 s, up to ~90 s). Refreshes the hero on success. */
export function useSync() {
  const queryClient = useQueryClient();
  return useMutation<Insights, ApiError>({
    mutationFn: () => api<Insights>("/api/user/sync", { method: "POST" }),
    onSuccess: (insights) => {
      queryClient.setQueryData(queryKeys.insights, insights);
      return queryClient.invalidateQueries({ queryKey: queryKeys.hero });
    },
  });
}

/** `PUT /hero/class`: 200 is the updated hero (and a CLASS_CHANGED event). */
export function useChangeClass() {
  const queryClient = useQueryClient();
  return useMutation<Hero, ApiError, ChangeClassRequest>({
    mutationFn: (body) => api<Hero>("/api/user/hero/class", { method: "PUT", body }),
    onSuccess: (hero) => {
      queryClient.setQueryData(queryKeys.hero, hero);
      void queryClient.invalidateQueries({ queryKey: ["hero", "events"] });
    },
    // 409 (no longer qualified) or 429 (cooldown): our `qualified` / `can_switch_at` are stale.
    onError: (error) => {
      if (error.status === 409 || error.status === 429) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.hero, exact: true });
      }
    },
  });
}

/**
 * `PUT /preferences` with the full object. Saves run one at a time (`scope`), so a slow
 * answer can't overwrite a newer one. The hero carries the zone and rest days, so it reloads.
 */
export function useSavePreferences() {
  const queryClient = useQueryClient();
  return useMutation<Preferences, ApiError, Preferences>({
    mutationFn: (body) => api<Preferences>("/api/user/preferences", { method: "PUT", body }),
    scope: { id: "preferences" },
    onSuccess: (saved) => afterPreferencesSaved(queryClient, saved),
  });
}

/**
 * What a saved `PUT /preferences` changes elsewhere: the hero (zone, rest days), and the Arena
 * (`public_profile` decides whether the user is on a board and ranked), so a board or rank
 * fetched before the save must not stay cached for its long staleTime.
 */
export function afterPreferencesSaved(queryClient: QueryClient, saved: Preferences): void {
  queryClient.setQueryData(queryKeys.preferences, saved);
  void queryClient.invalidateQueries({ queryKey: queryKeys.hero, exact: true });
  void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
  void queryClient.invalidateQueries({ queryKey: queryKeys.rank, exact: true });
}

/**
 * Browser zone on login (contract): if the stored zone is still the default UTC, send the
 * browser's zone once. Runs after the `#token` hand-off without blocking it; a failure is
 * ignored (Settings can fix the zone).
 */
export async function sendBrowserZone(queryClient: QueryClient): Promise<void> {
  try {
    const stored = await queryClient.fetchQuery<Preferences>({
      queryKey: queryKeys.preferences,
      queryFn: ({ signal }) => api<Preferences>("/api/user/preferences", { signal }),
    });
    const next = adoptBrowserZone(stored, browserTimeZone());
    if (!next) return;
    const saved = await api<Preferences>("/api/user/preferences", { method: "PUT", body: next });
    queryClient.setQueryData(queryKeys.preferences, saved);
    // The hero's dates and `day_ends_at` follow the zone.
    await queryClient.invalidateQueries({ queryKey: queryKeys.hero, exact: true });
  } catch {
    // Ignored on purpose: the user keeps UTC until they pick a zone in Settings.
  }
}

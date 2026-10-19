// TanStack Query hooks for every backend read, plus SAVE (`POST /sync`).
// Defaults (plan §4): no retry on 4xx, a long staleTime, no refetch on window focus,
// because `GET /insights` is limited to 20/min per user.

import { QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./api";
import type { ChangeClassRequest, Hero, HeroEvents, Insights, Preferences } from "./api/types";

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
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.preferences, saved);
      void queryClient.invalidateQueries({ queryKey: queryKeys.hero, exact: true });
    },
  });
}

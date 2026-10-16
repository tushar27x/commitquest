"use client";

import type { ReactNode } from "react";
import { useHero, useInsights } from "@/lib/queries";
import { useGameState } from "./GameState";
import { NewGameCard, ReconnectBanner, RoadBlockedCard } from "./StateCards";

export interface ScreenProps {
  /** The phone header (hidden from md up, where the top nav takes over). */
  header: ReactNode;
  /** The desktop page title. */
  title: string;
  children: ReactNode;
}

/**
 * A tab screen: phone header, sync banners, then the content. The content waits for the
 * two shell queries (insights + hero) and is replaced by NEW GAME (404) or THE ROAD IS
 * BLOCKED (500 / 502 / network, or anything else unexpected) when they can't load.
 */
export function Screen({ header, title, children }: ScreenProps) {
  const { needsReauth, syncBlocked, syncNotFound, save, saving } = useGameState();
  const insights = useInsights();
  const hero = useHero();
  const queries = [insights, hero];

  const notFound = syncNotFound || queries.some((q) => q.error?.status === 404);
  const failed = queries.filter((q) => q.error && q.error.status !== 404 && q.error.status !== 401);
  const blockedWithoutData = failed.some((q) => !q.data);
  const loading = queries.some((q) => q.isPending && !q.error);
  const retry = () => failed.forEach((q) => void q.refetch());
  const retrying = failed.some((q) => q.isFetching);

  let body: ReactNode;
  if (notFound) body = <NewGameCard />;
  else if (blockedWithoutData) body = <RoadBlockedCard onRetry={retry} retrying={retrying} />;
  else if (loading)
    body = (
      <p role="status" className="font-display text-[10px] text-muted-2">
        LOADING…
      </p>
    );
  else if (insights.data && hero.data) body = children;

  return (
    <>
      {header}
      <div className="mx-auto flex w-full max-w-[1180px] flex-col gap-5 px-3 pt-5 pb-6 md:px-6 md:pt-10">
        <h1 className="hidden font-display text-[20px] leading-[1.3] text-bone [text-shadow:4px_4px_0_var(--color-outline)] md:block">
          {title}
        </h1>
        {needsReauth && <ReconnectBanner />}
        {syncBlocked && <RoadBlockedCard onRetry={save} retrying={saving} />}
        {!blockedWithoutData && failed.length > 0 && <RoadBlockedCard onRetry={retry} retrying={retrying} />}
        {body}
      </div>
    </>
  );
}

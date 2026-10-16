"use client";

import type { ReactNode } from "react";
import { useHero, useInsights } from "@/lib/queries";
import { useGameState } from "./GameState";
import { NewGameCard, ReconnectBanner, RoadBlockedCard } from "./StateCards";

export interface ScreenProps {
  /** The phone header (hidden from md up, where the top nav takes over). */
  header: ReactNode;
  /** The page title: visible on desktop (the phone header shows its own). */
  title: string;
  /** Keep the title for screen readers only (Home has no visible title). */
  hideTitle?: boolean;
  /** Content runs edge to edge on phones (Home's scene); it pads itself. */
  flush?: boolean;
  children: ReactNode;
}

/**
 * A tab screen: phone header, sync banners, then the content. The content waits for the
 * two shell queries (insights + hero) and is replaced by NEW GAME (404) or THE ROAD IS
 * BLOCKED (500 / 502 / network, or anything else unexpected) when they can't load.
 */
export function Screen({ header, title, hideTitle = false, flush = false, children }: ScreenProps) {
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

  const banners = [
    needsReauth && <ReconnectBanner key="reauth" />,
    syncBlocked && <RoadBlockedCard key="sync" onRetry={save} retrying={saving} />,
    !blockedWithoutData && failed.length > 0 && <RoadBlockedCard key="query" onRetry={retry} retrying={retrying} />,
  ].filter(Boolean);
  // Flush screens only bleed their own content; banners and state cards keep the gutter.
  const gutter = flush ? "px-3 md:px-0" : "";

  return (
    <>
      {header}
      <div
        className={`mx-auto flex w-full max-w-[1180px] flex-col gap-5 pb-6 md:px-6 md:pt-10 ${
          flush ? "md:pb-14" : "px-3 pt-5"
        }`}
      >
        <h1
          className={
            hideTitle
              ? "sr-only"
              : "hidden font-display text-[20px] leading-[1.3] text-bone [text-shadow:4px_4px_0_var(--color-outline)] md:block"
          }
        >
          {title}
        </h1>
        {banners.length > 0 && <div className={`flex flex-col gap-5 ${flush ? "px-3 pt-5 md:p-0" : ""}`}>{banners}</div>}
        {flush && body !== children ? <div className={`${gutter} pt-5 md:pt-0`}>{body}</div> : body}
      </div>
    </>
  );
}

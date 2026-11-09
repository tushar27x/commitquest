"use client";

import Link from "next/link";
import { useState } from "react";
import { Portrait } from "@/components/art/Portrait";
import { HeartRow } from "@/components/HeartRow";
import { NewGameCard, RoadBlockedCard } from "@/components/game/StateCards";
import { Arrow } from "@/components/ui/Arrow";
import { buttonClass } from "@/components/ui/pixel-button";
import { NOT_IN_ARENA_TITLE, type Archetype, type Hero, type Leaderboard, type LeaderboardEntry, type Rank } from "@/lib/api/types";
import { CLASS_ORDER, classAccent } from "@/lib/art";
import { className } from "@/lib/journal";
import { useHero, useLeaderboard, useRank } from "@/lib/queries";
import { ArenaScene } from "./ArenaScene";

const CARD = "flex flex-col gap-2.5 bg-stone px-3.5 py-3 shadow-px md:px-5 md:py-[18px]";
const HEADING = "font-display text-[9px] leading-normal md:text-[10px]";
const PAGE_BUTTON = "px-3 py-2.5 text-[8px] md:text-[9px]";

/**
 * Arena (plan §4): the leaderboard of one class (B8, Phase 13), your place on your own board, and
 * a way to browse the other classes. Screen only renders it once the hero has loaded.
 */
export function ArenaView() {
  const hero = useHero().data;
  if (!hero) return null;
  return <Arena hero={hero} />;
}

function Arena({ hero }: { hero: Hero }) {
  const current = hero.class.current;
  // The board being read; null follows the hero's class (so changing class moves it).
  const [picked, setPicked] = useState<Archetype | null>(null);
  const [page, setPage] = useState(0);
  const archetype = picked ?? current;

  const choose = (next: Archetype) => {
    setPicked(next);
    setPage(0);
  };

  return (
    <div className="flex flex-col gap-4 md:flex-row md:flex-wrap md:items-start md:gap-8">
      <ArenaScene hero={hero} className="md:flex-[1_1_360px]" />
      <div className="flex min-w-0 flex-col gap-3.5 px-3 md:flex-[1.6_1_480px] md:gap-6 md:px-0">
        <RankCard hero={hero} onShow={(rank) => (setPicked(null), setPage(Math.floor((rank.rank - 1) / 20)))} />
        <ClassPicker selected={archetype} current={current} onChoose={choose} />
        <Board hero={hero} archetype={archetype} page={page} onPage={setPage} />
      </div>
    </div>
  );
}

/** Your place on your own board, or why you aren't on it. */
function RankCard({ hero, onShow }: { hero: Hero; onShow: (rank: Rank) => void }) {
  const rank = useRank();
  const cls = hero.class.current;

  if (rank.isPending) {
    return (
      <section aria-label="Your rank" className={CARD}>
        <p role="status" className={`${HEADING} text-muted-2`}>
          LOADING…
        </p>
      </section>
    );
  }
  if (rank.error) {
    // "Not in the Arena" is a state of the Arena; any other 404 is "no hero yet".
    if (rank.error.status === 404 && rank.error.problem?.title === NOT_IN_ARENA_TITLE) return <NotInArena detail={rank.error.problem.detail} />;
    if (rank.error.status === 404) return <NewGameCard />;
    if (rank.error.status === 401) return null;
    return <RoadBlockedCard onRetry={() => void rank.refetch()} retrying={rank.isFetching} />;
  }

  const { rank: place, total, top_percent } = rank.data;
  return (
    <section aria-labelledby="arena-rank" className={CARD}>
      <h2 id="arena-rank" className={`${HEADING} text-gold`}>
        YOUR PLACE
      </h2>
      <div className="flex items-center gap-3.5">
        <Portrait archetype={cls} />
        <div className="flex min-w-0 flex-col gap-1">
          <span className="font-display text-[18px] text-bone md:text-[22px]">#{place}</span>
          <p className="text-muted">
            Top {top_percent}% of {total} {total === 1 ? "hero" : "heroes"} on the{" "}
            <span className="font-display text-[10px]" style={{ color: classAccent(cls) }}>
              {cls}
            </span>{" "}
            board.
          </p>
        </div>
      </div>
      <button
        type="button"
        onClick={() => onShow(rank.data)}
        className="relative flex items-center gap-1.5 self-start py-2 font-display text-[8px] text-gold after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-[''] md:text-[9px]"
      >
        FIND ME ON THE BOARD <Arrow />
      </button>
    </section>
  );
}

/** 404 "Not in the Arena": the hero exists but isn't ranked (private profile, or GitHub access to renew). */
function NotInArena({ detail }: { detail?: string }) {
  return (
    <section aria-labelledby="arena-hidden" className={CARD}>
      <h2 id="arena-hidden" className={`${HEADING} text-muted-2`}>
        NOT IN THE ARENA
      </h2>
      <p className="text-muted">
        {detail ?? 'Your profile isn\'t ranked. Turn on "Show me in the Arena" in Settings, then log in again to refresh your stats.'}
      </p>
      <Link
        href="/settings"
        className="relative flex items-center gap-1.5 self-start py-2 font-display text-[8px] text-gold after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-[''] md:text-[9px]"
      >
        SHOW ME IN SETTINGS <Arrow />
      </Link>
    </section>
  );
}

/** One button per class: any signed-in hero can read any board. */
function ClassPicker({ selected, current, onChoose }: { selected: Archetype; current: Archetype; onChoose: (a: Archetype) => void }) {
  return (
    <section aria-label="Choose a board" className="flex flex-wrap gap-2.5">
      {CLASS_ORDER.map((cls) => {
        const on = cls === selected;
        return (
          <button
            key={cls}
            type="button"
            aria-pressed={on}
            aria-label={`${className(cls)} board${cls === current ? " (your class)" : ""}`}
            onClick={() => onChoose(cls)}
            className={`relative flex h-[56px] w-[48px] items-center justify-center bg-stone after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-[''] ${
              on ? "shadow-[0_0_0_3px_var(--color-gold)]" : "shadow-px hover:brightness-125"
            }`}
          >
            <Portrait archetype={cls} dimmed={!on} />
          </button>
        );
      })}
    </section>
  );
}

function Board({ hero, archetype, page, onPage }: { hero: Hero; archetype: Archetype; page: number; onPage: (p: number) => void }) {
  const board = useLeaderboard(archetype, page);
  const title = `${archetype} BOARD`;

  let body;
  if (board.isPending) {
    body = (
      <p role="status" className="font-display text-[10px] text-muted-2">
        LOADING…
      </p>
    );
  } else if (board.error && !board.data) {
    body =
      board.error.status === 404 ? (
        <NewGameCard />
      ) : (
        <RoadBlockedCard onRetry={() => void board.refetch()} retrying={board.isFetching} />
      );
  } else if (board.data && board.data.total === 0) {
    body = (
      <p className="text-muted">
        {className(archetype)} board is empty. {archetype === hero.class.current ? "Be the first: turn on \"Show me in the Arena\" in Settings." : "No hero has joined it yet."}
      </p>
    );
  } else if (board.data) {
    body = <Rows board={board.data} page={page} onPage={onPage} fetching={board.isFetching} />;
  }

  return (
    <section aria-labelledby="board-title" className={CARD}>
      <h2 id="board-title" className={HEADING} style={{ color: classAccent(archetype) }}>
        {title}
      </h2>
      {body}
    </section>
  );
}

function Rows({ board, page, onPage, fetching }: { board: Leaderboard; page: number; onPage: (p: number) => void; fetching: boolean }) {
  const pages = Math.max(1, Math.ceil(board.total / board.size));
  return (
    <>
      {board.entries.length === 0 ? (
        <p className="text-muted">Nothing on this page.</p>
      ) : (
        <ol aria-busy={fetching} className="flex flex-col gap-1.5">
          {board.entries.map((entry) => (
            <Row key={`${entry.rank}-${entry.login}`} entry={entry} />
          ))}
        </ol>
      )}
      {pages > 1 && (
        <nav aria-label="Board pages" className="flex items-center justify-between gap-3 pt-1">
          <button type="button" disabled={page <= 0} onClick={() => onPage(page - 1)} className={buttonClass("primary", PAGE_BUTTON)}>
            PREV
          </button>
          <span className="font-display text-[8px] text-muted-2">
            PAGE {page + 1} / {pages}
          </span>
          <button type="button" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} className={buttonClass("primary", PAGE_BUTTON)}>
            NEXT
          </button>
        </nav>
      )}
    </>
  );
}

function Row({ entry }: { entry: LeaderboardEntry }) {
  return (
    <li
      aria-current={entry.is_you ? "true" : undefined}
      className={`flex items-center gap-2.5 px-2 py-1.5 md:gap-3.5 md:px-3 ${entry.is_you ? "bg-stone-lit shadow-[0_0_0_3px_var(--color-gold)]" : ""}`}
    >
      <span className="w-9 shrink-0 font-display text-[10px] text-gold md:w-11 md:text-[12px]">#{entry.rank}</span>
      {/* eslint-disable-next-line @next/next/no-img-element -- a 32 px GitHub avatar; next/image would proxy it for nothing */}
      <img src={entry.avatar_url} alt="" width={32} height={32} loading="lazy" className="h-8 w-8 shrink-0 bg-night [image-rendering:pixelated]" />
      <span className="min-w-0 flex-1 truncate text-[22px] leading-none">
        {entry.login}
        {entry.is_you && <span className="text-gold"> (you)</span>}
      </span>
      <span className="shrink-0 text-right leading-none">
        <span className="font-display text-[8px] md:text-[9px]">LV {entry.level}</span>
        <span className="block text-[18px] text-muted-2">{entry.xp} XP</span>
      </span>
      <HeartRow lives={entry.lives} className="hidden gap-0.5 sm:flex" />
    </li>
  );
}

"use client";

import { PixelSprite } from "@/components/PixelSprite";
import { SegmentBar } from "@/components/ui/SegmentBar";
import { buttonClass } from "@/components/ui/pixel-button";
import type { Hero, HeroEvents } from "@/lib/api/types";
import { useNow } from "@/lib/clock";
import { activeDaysToHeart, activeDaysToLevel, heartsAtStake, isRestDay, recentLog, timeLeft, weekCells, type LogTone, type WeekCell } from "@/lib/hero";
import { classAccent } from "@/lib/sprites";

// Cards set their own shadow: two box-shadow utilities on one element fight over the order.
const PANEL = "flex flex-col bg-stone px-3.5 py-3 md:px-5 md:py-[18px]";
const CARD = `${PANEL} shadow-px`;

/** Desktop only: class, level and XP (the phone shows them in the header). */
export function LevelPanel({ hero }: { hero: Hero }) {
  const days = activeDaysToLevel(hero);
  return (
    <section aria-label="Level" className={`${CARD} hidden gap-3 md:flex`}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3">
        <h2 className="font-display text-[18px]" style={{ color: classAccent(hero.class.current) }}>
          {hero.class.current} · LV {hero.level}
        </h2>
        <span className="text-muted-2">
          {hero.xp} / {hero.xp_for_next} XP
        </span>
      </div>
      <SegmentBar
        filled={(hero.xp / hero.xp_for_next) * 20}
        cells={20}
        label={`${hero.xp} of ${hero.xp_for_next} XP to level ${hero.level + 1}`}
        className="h-3.5 gap-[3px]"
      />
      <p className="text-muted">
        {days} more active {days === 1 ? "day" : "days"} to LV {hero.level + 1}
      </p>
    </section>
  );
}

// A 7×6 tick for "FED TODAY" (the fonts have no ✓ glyph).
// It draws in currentColor, so the heading's text-success colours it.
const TICK = { grid: ["......k", ".....kk", "k...kk.", "kk.kk..", ".kkk...", "..k...."], palette: { k: "currentColor" } };

/** Today: fed, resting, or not fed yet with the countdown to local midnight. */
export function TodayCard({ hero }: { hero: Hero }) {
  const now = useNow();
  const { today } = hero;
  const resting = !today.active && isRestDay(hero);
  const hungry = hero.mood === "HUNGRY";

  if (today.active) {
    const parts = [
      today.calendar_count > 0 && `${today.calendar_count} ${today.calendar_count === 1 ? "contribution" : "contributions"}`,
      today.branch_commit_count > 0 && `${today.branch_commit_count} ${today.branch_commit_count === 1 ? "commit" : "commits"} on other branches`,
    ].filter(Boolean);
    return (
      <section aria-labelledby="today-title" className={`${CARD} gap-1.5 md:gap-2.5`}>
        <h2 id="today-title" className="flex items-center gap-2.5 font-display text-[10px] text-success md:text-[12px]">
          FED TODAY <PixelSprite {...TICK} scale={2} label="done" />
        </h2>
        <p className="leading-[1.05] text-muted md:text-[22px]">
          {parts.length ? `${parts.join(" and ")} today. ` : ""}Your hearts are safe until midnight.
        </p>
      </section>
    );
  }

  if (resting) {
    return (
      <section aria-labelledby="today-title" className={`${CARD} gap-1.5 md:gap-2.5`}>
        <h2 id="today-title" className="font-display text-[10px] text-muted md:text-[12px]">
          REST DAY
        </h2>
        <p className="leading-[1.05] text-muted md:text-[22px]">
          Missing today costs no heart and your streak waits for you. A contribution still counts.
        </p>
      </section>
    );
  }

  const left = timeLeft(today.day_ends_at, now);
  const [h, m] = left.split(":");
  return (
    <section
      aria-labelledby="today-title"
      className={`${PANEL} gap-1.5 md:gap-2.5 ${hungry ? "shadow-[0_0_0_4px_var(--color-outline),inset_0_0_0_3px_var(--color-ember)]" : "shadow-px"}`}
    >
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <h2 id="today-title" className={`font-display text-[10px] leading-normal md:text-[12px] ${hungry ? "text-ember" : "text-bone"}`}>
          NOT FED YET
        </h2>
        <span
          role="timer"
          aria-label={`${Number(h)} hours ${Number(m)} minutes left today`}
          className="font-display text-[9px] leading-normal md:text-[11px]"
        >
          {left} LEFT
        </span>
      </div>
      <p className="leading-[1.05] text-muted md:text-[22px] md:leading-[1.08]">
        Any <span className="hidden md:inline">GitHub </span>contribution before midnight keeps {heartsAtStake(hero.lives)}.
        <span className="hidden md:inline"> A commit, a PR, an issue or a review all count.</span>
      </p>
    </section>
  );
}

const CELL: Record<WeekCell["status"], string> = {
  active: "bg-gold text-ink",
  missed: "bg-ember-fill text-[#f6c0b0]",
  today: "bg-night text-gold",
  future: "bg-stone-lit text-muted-2",
  rest: "bg-rest text-muted",
};

/** Monday to Sunday: +10 active, -1 missed, Z rest, ? today, empty future. */
export function WeekCard({ hero }: { hero: Hero }) {
  const toHeart = activeDaysToHeart(hero);
  return (
    <section aria-labelledby="week-title" className={`${CARD} gap-2.5 md:gap-3.5`}>
      <h2 id="week-title" className="sr-only font-display text-[10px] md:not-sr-only">
        THIS WEEK
      </h2>
      <ol className="grid grid-cols-7 gap-1.5 md:gap-2">
        {weekCells(hero).map((cell) => (
          <li key={cell.date} className="flex flex-col items-center gap-[5px] md:gap-1.5">
            <span aria-hidden className="font-display text-[7px] text-muted-2 md:text-[8px]">
              {cell.letter}
            </span>
            <span
              aria-label={cell.label}
              className={`flex h-[30px] w-full items-center justify-center font-display text-[7px] md:h-9 md:text-[9px] ${CELL[cell.status]} ${
                cell.isToday
                  ? "shadow-[0_0_0_2px_var(--color-outline),inset_0_0_0_3px_var(--color-gold)]"
                  : "shadow-[0_0_0_2px_var(--color-outline)]"
              }`}
            >
              {cell.mark}
            </span>
          </li>
        ))}
      </ol>
      <p className="leading-none text-muted">
        {toHeart === null
          ? "Hearts are full. Keep the streak going."
          : `${toHeart} more active ${toHeart === 1 ? "day wins" : "days win"} a heart back.`}
      </p>
    </section>
  );
}

const TONE: Record<LogTone, string> = { bone: "text-bone", ember: "text-ember", gold: "text-gold", muted: "text-muted-2" };

/** RECENTLY: the last few days, newest first (3 on phone, 4 on desktop). */
export function RecentCard({ today, events }: { today: string; events: { data?: HeroEvents; isError: boolean; refetch: () => unknown; isFetching: boolean } }) {
  const log = events.data ? recentLog(events.data, today) : [];
  return (
    <section
      aria-labelledby="recent-title"
      className="flex flex-col gap-1.5 px-0.5 md:gap-2.5 md:bg-stone md:px-5 md:py-[18px] md:shadow-px"
    >
      <h2 id="recent-title" className="font-display text-[8px] text-muted-2 md:text-[10px] md:text-bone">
        RECENTLY
      </h2>
      {events.isError && !events.data ? (
        <p className="flex flex-wrap items-center gap-3 text-muted">
          The log didn&apos;t load.
          <button type="button" onClick={() => void events.refetch()} disabled={events.isFetching} className={buttonClass("ghost", "font-display text-[9px]")}>
            RETRY
          </button>
        </p>
      ) : events.data && log.length === 0 ? (
        <p className="text-muted">Nothing yet. Your first day counts tonight.</p>
      ) : (
        <ul className="flex flex-col gap-[5px] md:gap-2">
          {log.map((entry, i) => (
            <li key={entry.key} className={`grid grid-cols-[40px_minmax(0,1fr)] gap-2 leading-none md:grid-cols-[44px_minmax(0,1fr)] md:gap-2.5 md:text-[21px] ${i === 3 ? "max-md:hidden" : ""}`}>
              <span className="pt-[3px] font-display text-[7px] text-muted-2 md:text-[8px]">{entry.day}</span>
              <span className={TONE[entry.tone]}>{entry.text}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

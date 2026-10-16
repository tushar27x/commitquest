"use client";

import { useRef, useState, type KeyboardEvent } from "react";
import { MobileHeader } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { Parchment } from "@/components/Parchment";
import { Arrow } from "@/components/ui/Arrow";
import type { Insights } from "@/lib/api/types";
import { calendarDays, longDate, repoBars, shortDate, type CalendarDay } from "@/lib/journal";
import { useHero, useInsights } from "@/lib/queries";

const TABS = [
  { id: "stats", label: "STATS" },
  { id: "repos", label: "REPOS" },
  { id: "reviews", label: "REVIEWS" },
] as const;
type TabId = (typeof TABS)[number]["id"];

const SUBTITLE = "Your last 12 months";

/** Journal: the yearly insights, in STATS / REPOS / REVIEWS tabs. */
export function JournalScreen() {
  const [tab, setTab] = useState<TabId>("stats");
  const tabRefs = useRef<Record<TabId, HTMLButtonElement | null>>({ stats: null, repos: null, reviews: null });

  const select = (id: TabId, focus = false) => {
    setTab(id);
    if (focus) tabRefs.current[id]?.focus();
  };

  // ARIA tabs: arrows move between tabs, Home/End jump to the ends.
  const onKeyDown = (e: KeyboardEvent) => {
    const i = TABS.findIndex((t) => t.id === tab);
    const next = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: TABS.length - 1 }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    select(TABS[(next + TABS.length) % TABS.length].id, true);
  };

  return (
    <Screen
      title="JOURNAL"
      actions={<span className="text-muted-2">{SUBTITLE}</span>}
      header={
        <MobileHeader flat className="pt-4">
          <h1 className="font-display text-[14px] text-gold">JOURNAL</h1>
          <span className="ml-auto text-muted-2">{SUBTITLE}</span>
        </MobileHeader>
      }
    >
      <div
        role="tablist"
        aria-label="Journal sections"
        onKeyDown={onKeyDown}
        className="sticky top-14 z-10 -mx-3 -mt-5 grid grid-cols-3 gap-1.5 bg-stone px-4 pt-1 pb-3 shadow-bar-down md:static md:mx-0 md:mt-0 md:max-w-[480px] md:bg-transparent md:p-0 md:shadow-none"
      >
        {TABS.map((t) => {
          const selected = t.id === tab;
          return (
            <button
              key={t.id}
              ref={(el) => {
                tabRefs.current[t.id] = el;
              }}
              type="button"
              role="tab"
              id={`journal-tab-${t.id}`}
              aria-selected={selected}
              aria-controls={`journal-panel-${t.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => select(t.id)}
              className={`h-11 font-display text-[8px] shadow-[0_0_0_2px_var(--color-outline)] md:text-[10px] ${
                selected ? "bg-gold text-ink" : "bg-stone-lit text-muted hover:text-gold"
              }`}
            >
              {t.label}
            </button>
          );
        })}
      </div>
      <JournalPanel tab={tab} onReadMore={() => select("reviews", true)} />
    </Screen>
  );
}

function JournalPanel({ tab, onReadMore }: { tab: TabId; onReadMore: () => void }) {
  const insights = useInsights().data;
  const timeZone = useHero().data?.timezone ?? "UTC";
  if (!insights) return null;
  return (
    <div role="tabpanel" id={`journal-panel-${tab}`} aria-labelledby={`journal-tab-${tab}`} tabIndex={0} className="flex flex-col gap-4 md:gap-6">
      {tab === "stats" && <StatsPanel insights={insights} timeZone={timeZone} onReadMore={onReadMore} />}
      {tab === "repos" && <ReposPanel insights={insights} />}
      {tab === "reviews" && <ReviewsPanel insights={insights} />}
    </div>
  );
}

const CARD = "flex flex-col bg-stone shadow-btn-flat";
const fmt = (n: number) => n.toLocaleString("en-US");

function StatsPanel({ insights, timeZone, onReadMore }: { insights: Insights; timeZone: string; onReadMore: () => void }) {
  const { stats } = insights;
  const cards = [
    { label: "COMMITS", value: stats.total_commits, gold: true },
    { label: "PULL REQUESTS", value: stats.total_prs },
    { label: "ISSUES", value: stats.total_issues },
    { label: "STARS", value: stats.total_stars },
  ];
  return (
    <>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-6">
        {cards.map((c) => (
          <div key={c.label} className={`${CARD} gap-1.5 px-3 pt-3 pb-2.5 md:gap-2.5 md:p-5`}>
            <dt className="font-display text-[7px] text-muted-2 md:text-[9px]">{c.label}</dt>
            <dd className={`font-display text-[18px] md:text-[24px] ${c.gold ? "text-gold" : "text-bone"}`}>{fmt(c.value)}</dd>
          </div>
        ))}
      </dl>
      <div className="grid gap-4 md:grid-cols-2 md:items-start md:gap-6">
        <CalendarCard days={calendarDays(insights, timeZone)} />
        <TopReposCard insights={insights} />
      </div>
      <Parchment title="YEAR IN REVIEW" titleId="year-teaser-title">
        <p className="line-clamp-2 leading-[1.05] md:text-[22px]">{insights.year_in_review}</p>
        <button
          type="button"
          onClick={onReadMore}
          className="flex items-center gap-2 self-end py-1.5 font-display text-[7px] text-parchment-heading hover:underline md:text-[9px]"
        >
          READ MORE <Arrow />
        </button>
      </Parchment>
    </>
  );
}

const SHADES = ["bg-stone-lit", "bg-[#7a5a22]", "bg-[#b8862a]", "bg-gold"];

function CalendarCard({ days }: { days: CalendarDay[] }) {
  const active = days.filter((d) => d.count > 0).length;
  const summary = `Contribution calendar for the last ${days.length} days, ${active} active days`;
  return (
    <section aria-labelledby="calendar-title" className={`${CARD} gap-2.5 px-3 pt-3 pb-3.5 md:gap-3.5 md:p-5`}>
      <div className="flex items-baseline justify-between">
        <h2 id="calendar-title" className="font-display text-[8px] md:text-[10px]">
          LAST {days.length} DAYS
        </h2>
        <span className="text-muted-2">{active} active</span>
      </div>
      <div role="img" aria-label={summary} className="grid grid-cols-14 gap-1 md:gap-1.5">
        {days.map((d) => (
          <span
            key={d.date}
            title={`${longDate(d.date)}: ${d.count}`}
            className={`aspect-square shadow-[0_0_0_1px_var(--color-outline)] ${SHADES[d.level]}`}
          />
        ))}
      </div>
      {days.length > 0 && (
        <div aria-hidden className="flex justify-between font-display text-[7px] text-faint md:text-[8px]">
          <span>{shortDate(days[0].date)}</span>
          <span>{shortDate(days[days.length - 1].date)}</span>
        </div>
      )}
      {/* The text alternative for the chart. */}
      <ul className="sr-only">
        {days.map((d) => (
          <li key={d.date}>
            {longDate(d.date)}: {d.count} {d.count === 1 ? "contribution" : "contributions"}
          </li>
        ))}
      </ul>
    </section>
  );
}

function RepoBarRow({ name, count, pct }: { name: string; count: number; pct: number }) {
  return (
    <>
      <span className="truncate leading-none">{name}</span>
      <span aria-hidden className="block h-2.5 bg-stone-lit">
        <span className="block h-full bg-gold" style={{ width: `${pct}%` }} />
      </span>
      <span className="text-right text-muted">
        {count}
        <span className="sr-only"> commits</span>
      </span>
    </>
  );
}

function TopReposCard({ insights }: { insights: Insights }) {
  const repos = repoBars(insights.stats.ranked_repos).slice(0, 3);
  return (
    <section aria-labelledby="repos-title" className={`${CARD} gap-2.5 px-3 pt-3 pb-3.5 md:gap-3.5 md:p-5`}>
      <h2 id="repos-title" className="font-display text-[8px] md:text-[10px]">
        TOP REPOS
      </h2>
      {repos.length === 0 ? (
        <p className="text-muted">No commits to public repos this year.</p>
      ) : (
        <ol className="flex flex-col gap-2">
          {repos.map((r) => (
            <li key={r.name} className="grid grid-cols-[120px_minmax(0,1fr)_32px] items-center gap-2.5">
              <RepoBarRow {...r} />
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function ReposPanel({ insights }: { insights: Insights }) {
  const repos = repoBars(insights.stats.ranked_repos);
  if (repos.length === 0) return <p className="text-muted">No commits to public repos this year.</p>;
  return (
    <ol className="grid gap-4 md:grid-cols-2 md:gap-6">
      {repos.map((r, i) => {
        const summary = insights.repo_summaries[r.name];
        return (
          <li key={r.name} className={`${CARD} gap-3 p-3 md:p-5`}>
            <div className="flex items-baseline justify-between gap-3">
              <h2 className="truncate text-[22px] leading-none md:text-[24px]">
                <span className="mr-2 font-display text-[8px] text-faint">#{i + 1}</span>
                {r.name}
              </h2>
              <span className="shrink-0 text-muted">{fmt(r.count)} commits</span>
            </div>
            <span aria-hidden className="block h-2.5 bg-stone-lit">
              <span className="block h-full bg-gold" style={{ width: `${r.pct}%` }} />
            </span>
            {summary && (
              <Parchment>
                <p className="leading-[1.05] md:text-[22px]">{summary}</p>
              </Parchment>
            )}
          </li>
        );
      })}
    </ol>
  );
}

function ReviewsPanel({ insights }: { insights: Insights }) {
  const reviews = [
    { id: "year", title: "YEAR IN REVIEW", text: insights.year_in_review },
    { id: "week", title: "THIS WEEK", text: insights.weekly_review },
    { id: "craft", title: "YOUR CRAFT", text: insights.craft_review },
  ];
  return (
    <div className="flex max-w-[760px] flex-col gap-5 md:gap-7">
      {reviews.map((r) => (
        <Parchment key={r.id} title={r.title} titleId={`review-${r.id}`}>
          <p className="leading-[1.08] whitespace-pre-line md:text-[24px] md:leading-[1.1]">{r.text}</p>
        </Parchment>
      ))}
    </div>
  );
}

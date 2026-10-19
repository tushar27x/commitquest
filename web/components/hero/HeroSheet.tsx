"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type KeyboardEvent, type Ref } from "react";
import { MobileHeader } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { Parchment } from "@/components/Parchment";
import { PixelSprite } from "@/components/PixelSprite";
import { Arrow } from "@/components/ui/Arrow";
import { SegmentBar } from "@/components/ui/SegmentBar";
import { buttonClass } from "@/components/ui/pixel-button";
import type { Archetype, Hero, Insights } from "@/lib/api/types";
import { CLASS_INFO, CLASS_SCORING, classSuggestion } from "@/lib/classes";
import { className, scoreRows } from "@/lib/journal";
import { useHero, useInsights } from "@/lib/queries";
import { classAccent, classSprite } from "@/lib/sprites";

/** Hero: the character sheet (docs/design/png/character-sheet-*.png). */
export function HeroSheetScreen() {
  const level = useHero().data?.level;
  return (
    <Screen
      title="HERO"
      actions={
        <Link href="/hero/change" className={buttonClass("primary", "px-3.5 py-3 text-[10px]")}>
          CHANGE CLASS
        </Link>
      }
      header={
        <MobileHeader className="justify-between">
          <span className="w-16 font-display text-[9px] text-muted-2">{level ? `LV ${level}` : ""}</span>
          <h1 className="font-display text-[11px]">HERO</h1>
          <Link href="/hero/change" className="flex w-16 items-center justify-end gap-1.5 py-3 font-display text-[8px] text-gold">
            CHANGE <Arrow />
          </Link>
        </MobileHeader>
      }
    >
      <HeroSheet />
    </Screen>
  );
}

function Pedestal({ size }: { size: "phone" | "desktop" }) {
  return (
    <div
      aria-hidden
      className={`bg-pedestal shadow-[0_4px_0_var(--color-outline),inset_0_4px_0_#5c5580] ${size === "phone" ? "h-3.5 w-[150px]" : "h-4 w-[190px]"}`}
    />
  );
}

function HeroSheet() {
  const hero = useHero().data;
  const insights = useInsights().data;
  if (!hero || !insights) return null;
  const current = hero.class.current;

  return (
    <div className="flex flex-col gap-5 md:flex-row md:flex-wrap md:items-start md:gap-8">
      <section aria-label="Your character" className="flex min-w-0 flex-col gap-5 md:flex-[1_1_420px] md:gap-[22px] md:bg-stone md:p-7 md:shadow-px-drop">
        {/* Phone: centred stack. */}
        <div className="flex flex-col items-center gap-2.5 md:hidden">
          <div className="flex flex-col items-center pt-1.5">
            <PixelSprite {...classSprite(current)} scale={8} label={`${className(current)} sprite`} />
            <Pedestal size="phone" />
          </div>
          <p className="mt-1.5 font-display text-[18px] [text-shadow:3px_3px_0_var(--color-outline)]" style={{ color: classAccent(current) }}>
            {current}
          </p>
          <p className="max-w-[300px] text-center text-[22px] leading-[1.05] text-muted">{CLASS_INFO[current].habit}</p>
          <ClassNote hero={hero} insights={insights} />
        </div>
        {/* Desktop: sprite beside the name. */}
        <div className="hidden flex-wrap items-end gap-7 md:flex">
          <div className="flex flex-col items-center">
            <PixelSprite {...classSprite(current)} scale={10} label={`${className(current)} sprite`} />
            <Pedestal size="desktop" />
          </div>
          <div className="flex flex-[1_1_200px] flex-col gap-2.5 pb-2">
            <span className="font-display text-[9px] text-muted-2">YOUR CLASS · SCORE {insights.archetype_scores[current] ?? 0}</span>
            <span className="font-display text-[26px] [text-shadow:4px_4px_0_var(--color-outline)]" style={{ color: classAccent(current) }}>
              {current}
            </span>
            <span className="text-[24px] leading-[1.05] text-muted">{CLASS_INFO[current].habit}</span>
            <ClassNote hero={hero} insights={insights} />
          </div>
        </div>
        {insights.character_sheet ? (
          <Parchment title={insights.character_sheet.title.toUpperCase()} titleId="lore-title" className="md:gap-2.5">
            <p className="text-[21px] leading-[1.08] md:text-[24px] md:leading-[1.1]">{insights.character_sheet.lore}</p>
          </Parchment>
        ) : (
          <p className="text-muted-2">Your lore gets written on your next save.</p>
        )}
      </section>
      <ScoresCard current={current} scores={insights.archetype_scores} />
    </div>
  );
}

/** A chosen class that isn't the top score: say where the habits point now (plan §2). */
function ClassNote({ hero, insights }: { hero: Hero; insights: Insights }) {
  const suggestion = classSuggestion(hero.class, insights.archetype_scores);
  if (!suggestion) return null;
  const { scored, belowThreshold: weak } = suggestion;
  return (
    <p className={`text-center md:text-left ${weak ? "text-ember" : "text-muted-2"}`}>
      {weak ? "Your habits now point to " : "Chosen class. Your top score is "}
      <span style={{ color: classAccent(scored) }}>{className(scored)}</span>.
    </p>
  );
}

function ScoresCard({ current, scores }: { current: Archetype; scores: Insights["archetype_scores"] }) {
  const rows = scoreRows(scores);
  // One "about" panel open at a time.
  const [open, setOpen] = useState<Archetype | null>(null);
  const buttons = useRef(new Map<Archetype, HTMLButtonElement>());
  const panel = useRef<HTMLDivElement>(null);

  // Opening moves focus into the panel, so screen readers read it and Tab continues inside.
  useEffect(() => {
    if (open) panel.current?.focus();
  }, [open]);

  const close = (archetype: Archetype) => {
    setOpen(null);
    buttons.current.get(archetype)?.focus();
  };
  const onKeyDown = (archetype: Archetype) => (e: KeyboardEvent) => {
    if (e.key === "Escape" && open === archetype) {
      e.preventDefault();
      close(archetype);
    }
  };

  return (
    <section aria-labelledby="scores-title" className="flex min-w-0 flex-col gap-3 md:flex-[1_1_420px] md:gap-[18px] md:bg-stone md:p-7 md:shadow-px-drop">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="scores-title" className="shrink-0 font-display text-[10px] md:text-[12px]">
          CLASS SCORES
        </h2>
        <span className="text-muted-2 md:hidden">0–100 · ALGO v1</span>
        <span className="hidden text-right text-muted-2 md:inline">0–100 · highest wins · ties go to roster order</span>
      </div>
      <ol className="flex flex-col gap-2 md:gap-2.5">
        {rows.map((row) => {
          const accent = classAccent(row.archetype);
          const name = className(row.archetype);
          const label = `${name} ${row.score} out of 100`;
          const expanded = open === row.archetype;
          const panelId = `about-${row.archetype.toLowerCase()}`;
          return (
            <li key={row.archetype} className="flex flex-col gap-2" onKeyDown={onKeyDown(row.archetype)}>
              <div
                className={`grid grid-cols-[32px_84px_minmax(0,1fr)_28px_24px] items-center gap-2 px-1.5 py-1 md:grid-cols-[40px_120px_minmax(0,1fr)_40px_28px] md:gap-3 md:px-2 md:py-1.5 ${
                  row.top ? "bg-stone-lit shadow-[0_0_0_2px_var(--color-gold)]" : ""
                }`}
              >
                <PixelSprite {...classSprite(row.archetype)} scale={2} className="md:m-1" />
                <span className="font-display text-[8px] md:text-[10px]" style={{ color: accent }}>
                  {row.archetype}
                  {row.top && <span className="sr-only"> (top score)</span>}
                  {row.archetype === current && <span className="sr-only"> (your class)</span>}
                </span>
                <SegmentBar filled={row.score / 10} cells={10} label={label} fillColor={accent} className="h-3 gap-0.5 md:hidden" />
                <SegmentBar filled={row.score / 5} cells={20} label={label} fillColor={accent} className="hidden h-4 gap-[3px] md:grid" />
                <span aria-hidden className="text-right md:text-[24px]">
                  {row.score}
                </span>
                <button
                  type="button"
                  ref={(el) => {
                    if (el) buttons.current.set(row.archetype, el);
                    else buttons.current.delete(row.archetype);
                  }}
                  aria-expanded={expanded}
                  aria-controls={panelId}
                  aria-label={`About ${name}`}
                  onClick={() => (expanded ? close(row.archetype) : setOpen(row.archetype))}
                  className={`relative flex size-6 items-center justify-center font-display text-[9px] after:absolute after:-inset-2.5 after:content-[''] md:size-7 md:text-[10px] ${
                    expanded
                      ? "bg-gold text-outline shadow-[0_0_0_2px_var(--color-outline)]"
                      : "bg-stone-lit text-muted shadow-[0_0_0_2px_var(--color-outline)] hover:text-gold active:text-gold"
                  }`}
                >
                  i
                </button>
              </div>
              {expanded && <ClassAbout archetype={row.archetype} id={panelId} ref={panel} />}
            </li>
          );
        })}
      </ol>
    </section>
  );
}

/** What earns a class (the backend scorer, ALGO v1). Stone, not parchment: this isn't AI text. */
function ClassAbout({ archetype, id, ref }: { archetype: Archetype; id: string; ref: Ref<HTMLDivElement> }) {
  const { signals, tip, note } = CLASS_SCORING[archetype];
  const titleId = `${id}-title`;
  return (
    <div
      id={id}
      ref={ref}
      role="region"
      aria-labelledby={titleId}
      tabIndex={-1}
      className="mx-1.5 flex flex-col gap-2.5 bg-stone-lit px-3.5 py-3 shadow-[0_0_0_2px_var(--color-outline)] outline-none focus-visible:shadow-[0_0_0_2px_var(--color-gold)] md:mx-2 md:px-4"
    >
      <h3 id={titleId} className="font-display text-[8px] leading-normal md:text-[9px]" style={{ color: classAccent(archetype) }}>
        WHAT EARNS {archetype}
      </h3>
      <ul className="flex flex-col gap-2">
        {signals.map((signal) => (
          <li key={signal.name} className="grid grid-cols-[44px_minmax(0,1fr)] items-baseline gap-2">
            <span className="font-display text-[9px] text-gold">{signal.weight}%</span>
            <span className="leading-[1.05]">
              <span className="text-bone">{signal.name}</span>
              <span className="text-muted">: {signal.measure}. </span>
              <span className="whitespace-nowrap text-muted-2">
                0 at {signal.from}, full at {signal.to}
              </span>
            </span>
          </li>
        ))}
      </ul>
      {note && <p className="leading-[1.05] text-muted">{note}</p>}
      <p className="leading-[1.05] text-success">
        <span className="font-display text-[8px]">TIP </span>
        {tip}
      </p>
      <p className="text-[18px] leading-[1.05] text-muted-2">
        Scores come from your public GitHub activity at your last sync. Your top score decides the class unless you pick one (score
        40+). Commit and PR samples are small, so these are rough.
      </p>
    </div>
  );
}

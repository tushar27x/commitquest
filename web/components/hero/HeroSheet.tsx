"use client";

import Link from "next/link";
import { MobileHeader } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { Parchment } from "@/components/Parchment";
import { PixelSprite } from "@/components/PixelSprite";
import { Arrow } from "@/components/ui/Arrow";
import { SegmentBar } from "@/components/ui/SegmentBar";
import { buttonClass } from "@/components/ui/pixel-button";
import type { Archetype, Hero, Insights } from "@/lib/api/types";
import { CLASS_INFO } from "@/lib/classes";
import { className, scoreRows } from "@/lib/journal";
import { useHero, useInsights } from "@/lib/queries";
import { classAccent, classSprite } from "@/lib/sprites";

/** Matches the backend's qualification threshold (contract: score ≥ 40 or the top class). */
const QUALIFY_SCORE = 40;

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
  const { chosen, scored } = hero.class;
  if (!chosen || chosen === scored) return null;
  const weak = (insights.archetype_scores[chosen] ?? 0) < QUALIFY_SCORE;
  return (
    <p className={`text-center md:text-left ${weak ? "text-ember" : "text-muted-2"}`}>
      {weak ? "Your habits now point to " : "Chosen class. Your top score is "}
      <span style={{ color: classAccent(scored) }}>{className(scored)}</span>.
    </p>
  );
}

function ScoresCard({ current, scores }: { current: Archetype; scores: Insights["archetype_scores"] }) {
  const rows = scoreRows(scores);
  return (
    <section aria-labelledby="scores-title" className="flex min-w-0 flex-col gap-3 md:flex-[1_1_420px] md:gap-[18px] md:bg-stone md:p-7 md:shadow-px-drop">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="scores-title" className="font-display text-[10px] md:text-[12px]">
          CLASS SCORES
        </h2>
        <span className="text-muted-2 md:hidden">0–100 · ALGO v1</span>
        <span className="hidden text-right text-muted-2 md:inline">0–100 · highest wins · ties go to roster order</span>
      </div>
      <ol className="flex flex-col gap-2 md:gap-2.5">
        {rows.map((row) => {
          const accent = classAccent(row.archetype);
          const label = `${className(row.archetype)} ${row.score} out of 100`;
          return (
            <li
              key={row.archetype}
              className={`grid grid-cols-[32px_92px_minmax(0,1fr)_32px] items-center gap-2 px-1.5 py-1 md:grid-cols-[40px_120px_minmax(0,1fr)_40px] md:gap-3 md:px-2 md:py-1.5 ${
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
            </li>
          );
        })}
      </ol>
    </section>
  );
}

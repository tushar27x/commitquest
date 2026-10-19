"use client";

import { useState } from "react";
import { RoadBlockedCard } from "@/components/game/StateCards";
import { PixelSprite } from "@/components/PixelSprite";
import { buttonClass } from "@/components/ui/pixel-button";
import { Switch } from "@/components/ui/Switch";
import type { Archetype, Hero, Insights } from "@/lib/api/types";
import { CLASS_COOLDOWN_DAYS, CLASS_INFO, QUALIFY_SCORE, classAction, classOptions, classSuggestion, cooldownLeft, type ClassAction, type ClassOption, type ClassPick } from "@/lib/classes";
import { clockNow, useNow } from "@/lib/clock";
import { formatClock, formatWait } from "@/lib/format";
import { className, localDate, longDate } from "@/lib/journal";
import { useChangeClass, useHero, useInsights } from "@/lib/queries";
import { classAccent, classSprite } from "@/lib/sprites";

/** Change class (docs/design/png/change-class-phone.png). Rendered inside <Screen flush>. */
export function ChangeClass() {
  const hero = useHero().data;
  const insights = useInsights().data;
  if (!hero || !insights) return null;
  return <ChangeClassForm hero={hero} insights={insights} />;
}

type Outcome =
  | { kind: "changed"; action: ClassAction; picked: ClassPick; scored: Archetype }
  | { kind: "not-qualified"; picked: Archetype }
  | { kind: "cooldown" }
  | { kind: "invalid" }
  | { kind: "blocked" };

function ChangeClassForm({ hero, insights }: { hero: Hero; insights: Insights }) {
  const cls = hero.class;
  const change = useChangeClass();
  // null = follow the top score (`chosen_archetype = NULL`).
  const [picked, setPicked] = useState<ClassPick>(cls.chosen);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  // A 429 from the server wins over a stale `can_switch_at`.
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const now = useNow(30_000);

  const options = classOptions(insights.archetype_scores, cls);
  const suggestion = classSuggestion(cls, insights.archetype_scores);
  const following = picked === null;
  // What the preview shows: following means the top class.
  const shown = picked ?? cls.scored;
  const action = classAction(cls, picked);
  const same = action === "none";
  const waitMs = cooldownLeft(cls, now, cooldownUntil);
  const cooling = waitMs > 0;

  const submit = () => {
    if (same || cooling || change.isPending) return;
    setOutcome(null);
    change.mutate(
      { archetype: picked },
      {
        onSuccess: (updated) => setOutcome({ kind: "changed", action, picked, scored: updated.class.scored }),
        onError: (error) => {
          switch (error.status) {
            case 401:
              return; // The game shell shows "Session ended".
            case 409:
              setOutcome({ kind: "not-qualified", picked: picked ?? cls.scored });
              return;
            case 429:
              setCooldownUntil(clockNow() + (error.retryAfter ?? 60) * 1000);
              setOutcome({ kind: "cooldown" });
              return;
            case 400:
              setOutcome({ kind: "invalid" });
              return;
            default:
              setOutcome({ kind: "blocked" });
          }
        },
      },
    );
  };

  let confirm: string;
  if (change.isPending) confirm = "CHANGING…";
  else if (cooling) confirm = `NEXT CHANGE IN ${formatWait(waitMs)}`;
  else if (action === "none") confirm = following ? "FOLLOWING TOP SCORE" : "THIS IS YOUR CLASS";
  else if (action === "follow") confirm = "FOLLOW TOP SCORE";
  else if (action === "pin") confirm = `PIN ${picked}`;
  else confirm = `BECOME ${picked}`;

  return (
    <div className="flex flex-col md:grid md:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] md:items-start md:gap-8">
      <Preview archetype={shown} following={following} />

      <div className="flex flex-col gap-4 px-3 pt-3.5 md:p-0">
        {cooling && <CooldownNote until={now + waitMs} waitMs={waitMs} timeZone={hero.timezone} />}
        {suggestion && (
          <p className={`bg-stone px-3.5 py-2.5 shadow-[0_0_0_2px_var(--color-outline)] ${suggestion.belowThreshold ? "text-ember" : "text-muted"}`}>
            {suggestion.belowThreshold
              ? `${cls.chosen} dropped below ${QUALIFY_SCORE}. You keep it, but your habits now point to `
              : "Your habits now point to "}
            <span style={{ color: classAccent(suggestion.scored) }}>{suggestion.scored}</span>.
          </p>
        )}

        <ul aria-label="Classes, highest score first" className="flex flex-col gap-1.5">
          {options.map((option) => (
            <li key={option.archetype}>
              <ClassRow option={option} selected={shown === option.archetype} onPick={() => setPicked(option.archetype)} />
            </li>
          ))}
        </ul>

        <div className="flex items-center justify-between gap-3 bg-stone px-3.5 py-2.5 shadow-[0_0_0_2px_var(--color-outline)]">
          <div className="flex min-w-0 flex-col gap-1">
            <span id="follow-title" className="font-display text-[8px] leading-normal">
              FOLLOW MY TOP SCORE
            </span>
            <span id="follow-help" className="text-[18px] leading-none text-muted-2">
              Your class changes with your habits ({cls.scored} now)
            </span>
          </div>
          <Switch
            checked={following}
            onChange={(on) => setPicked(on ? null : cls.current)}
            labelledBy="follow-title"
            describedBy="follow-help"
          />
        </div>

        <div className="flex flex-col gap-2.5 pt-1 pb-1.5">
          <button
            type="button"
            onClick={submit}
            disabled={same || cooling || change.isPending}
            className={buttonClass("primary", "w-full py-4 text-[11px]")}
          >
            {confirm}
          </button>
          <p className="text-center text-[19px] leading-[1.05] text-muted-2">
            {action === "pin"
              ? `Pinning keeps ${picked} even when your scores change. You keep LV ${hero.level} and your hearts; the next change opens in ${CLASS_COOLDOWN_DAYS} days.`
              : `You keep LV ${hero.level} and your hearts. After switching, the next change opens in ${CLASS_COOLDOWN_DAYS} days.`}
          </p>
          <OutcomeNote outcome={outcome} onRetry={submit} retrying={change.isPending} />
        </div>
      </div>
    </div>
  );
}

/** The picked class, big, with its habit line. Phone: a 176 px band; desktop: a panel. */
function Preview({ archetype, following }: { archetype: Archetype; following: boolean }) {
  const accent = classAccent(archetype);
  return (
    <section
      aria-live="polite"
      aria-label="Picked class"
      className="flex h-[176px] items-center gap-[18px] bg-[linear-gradient(#1a1630,#1a1630_74%,#241e40_74%)] px-5 md:h-auto md:flex-col md:items-start md:gap-5 md:bg-stone md:bg-none md:p-7 md:shadow-px-drop"
    >
      <div className="flex shrink-0 flex-col items-center">
        {/* PixelSprite sets display inline, so the breakpoints go on wrappers. */}
        <div className="md:hidden">
          <PixelSprite {...classSprite(archetype)} scale={6} />
        </div>
        <div className="hidden md:block">
          <PixelSprite {...classSprite(archetype)} scale={10} />
        </div>
        <div aria-hidden className="h-2.5 w-28 bg-pedestal shadow-bar-down md:h-4 md:w-[190px]" />
      </div>
      <div className="flex min-w-0 flex-col gap-2">
        {following && <span className="font-display text-[7px] leading-normal text-success md:text-[9px]">FOLLOWING TOP SCORE</span>}
        <span className="font-display text-[14px] md:text-[22px] md:[text-shadow:4px_4px_0_var(--color-outline)]" style={{ color: accent }}>
          {archetype}
        </span>
        <span className="text-[20px] leading-[1.05] text-muted md:text-[24px]">{CLASS_INFO[archetype].habit}</span>
      </div>
    </section>
  );
}

const TAG_INK: Record<string, string> = {
  CURRENT: "text-gold",
  "TOP SCORE": "text-success",
  QUALIFIED: "text-success",
};

function ClassRow({ option, selected, onPick }: { option: ClassOption; selected: boolean; onPick: () => void }) {
  const { archetype, score, selectable, tag } = option;
  const name = className(archetype);
  const label = `${name}, score ${score}, ${selectable ? tag.toLowerCase() : `locked, needs ${QUALIFY_SCORE}`}`;
  return (
    <button
      type="button"
      disabled={!selectable}
      aria-pressed={selected}
      aria-label={label}
      onClick={onPick}
      className={`grid h-11 w-full grid-cols-[32px_minmax(0,104px)_minmax(0,1fr)_auto] items-center gap-2.5 px-2.5 text-left disabled:cursor-not-allowed disabled:opacity-55 md:h-12 ${
        selected
          ? "bg-stone-lit shadow-[0_0_0_3px_var(--color-gold)]"
          : "bg-stone shadow-[0_0_0_2px_var(--color-outline)] hover:bg-stone-lit active:bg-stone-lit disabled:hover:bg-stone"
      }`}
    >
      <PixelSprite {...classSprite(archetype)} scale={2} />
      <span className="truncate font-display text-[9px]" style={{ color: selectable ? classAccent(archetype) : "var(--color-faint)" }}>
        {archetype}
      </span>
      <span className="text-muted">{score}</span>
      <span className={`font-display text-[7px] ${TAG_INK[tag] ?? "text-faint"}`}>{tag}</span>
    </button>
  );
}

function CooldownNote({ until, waitMs, timeZone }: { until: number; waitMs: number; timeZone: string }) {
  const at = new Date(until).toISOString();
  return (
    <p role="status" className="bg-stone px-3.5 py-2.5 text-muted shadow-[0_0_0_2px_var(--color-outline)]">
      <span className="font-display text-[8px] leading-normal text-ember">CLASS COOLDOWN · {formatWait(waitMs)}</span>
      <br />
      The next change opens {longDate(localDate(at, timeZone))} at {formatClock(at, timeZone)}.
    </p>
  );
}

function OutcomeNote({ outcome, onRetry, retrying }: { outcome: Outcome | null; onRetry: () => void; retrying: boolean }) {
  if (outcome?.kind === "blocked") return <RoadBlockedCard onRetry={onRetry} retrying={retrying} />;
  let text: string | null = null;
  let tone = "text-muted";
  switch (outcome?.kind) {
    case "changed":
      tone = "text-success";
      text =
        outcome.picked === null
          ? `Your class follows your top score: ${className(outcome.scored)}.`
          : outcome.action === "pin"
            ? `Pinned ${className(outcome.picked)}. It stays when your scores change.`
            : `You are now a ${className(outcome.picked)}. Level and hearts kept.`;
      break;
    case "not-qualified":
      tone = "text-ember";
      text = `${className(outcome.picked)} doesn't qualify any more: your scores changed. The list is up to date after your next save.`;
      break;
    case "cooldown":
      tone = "text-ember";
      text = "Your class changed recently. Wait for the cooldown to end.";
      break;
    case "invalid":
      tone = "text-ember";
      text = "The server doesn't know that class.";
      break;
  }
  return (
    <p role="status" className={`min-h-0 text-center ${tone}`}>
      {text}
    </p>
  );
}

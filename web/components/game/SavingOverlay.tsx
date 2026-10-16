"use client";

import { useEffect, useRef, useState } from "react";
import { SegmentBar } from "@/components/ui/SegmentBar";
import { StateCard } from "@/components/ui/StateCard";
import { buttonClass } from "@/components/ui/pixel-button";
import type { Archetype } from "@/lib/api/types.tmp";
import { CLASS_INFO } from "@/lib/classes";
import { CLASS_ORDER } from "@/lib/sprites";
import { useHero } from "@/lib/queries";

const CELLS = 12;
const TICK_MS = 500;
const TIP_MS = 5_000;
/** A sync usually takes under 30 s: the bar eases toward full and never claims to be done. */
const EASE_MS = 25_000;

const GAME_TIPS = [
  "A rest day costs no heart and doesn't break your streak.",
  "Three active days in a row win a heart back.",
  "Any commit on any branch feeds your hero.",
];

function tipsFor(current: Archetype | undefined): string[] {
  const order = current ? [current, ...CLASS_ORDER.filter((c) => c !== current)] : CLASS_ORDER;
  return [...order.map((c) => CLASS_INFO[c].tip), ...GAME_TIPS];
}

/** Full-screen "SAVING YOUR QUEST…" while POST /sync runs (10–90 s). */
export function SavingOverlay() {
  const dialog = useRef<HTMLDialogElement>(null);
  const [startedAt] = useState(() => Date.now());
  const [elapsed, setElapsed] = useState(0);
  const current = useHero().data?.class.current;
  const tips = tipsFor(current);

  useEffect(() => {
    const el = dialog.current;
    if (el && !el.open) el.showModal();
    const timer = window.setInterval(() => setElapsed(Date.now() - startedAt), TICK_MS);
    return () => {
      window.clearInterval(timer);
      el?.close();
    };
  }, [startedAt]);

  const filled = Math.min(CELLS - 1, Math.floor(CELLS * (1 - Math.exp(-elapsed / EASE_MS))) + 1);
  const tip = tips[Math.floor(elapsed / TIP_MS) % tips.length];

  return (
    <dialog
      ref={dialog}
      aria-labelledby="saving-title"
      // Saving can't be cancelled: the request keeps running on the server anyway.
      onCancel={(e) => e.preventDefault()}
      className="m-0 h-dvh max-h-none w-full max-w-none items-center justify-center bg-night/95 p-4 text-bone open:flex backdrop:bg-transparent"
    >
      <StateCard
        tone="gold"
        title="SAVING YOUR QUEST…"
        titleId="saving-title"
        role="status"
        className="w-full max-w-[420px]"
        meter={<SegmentBar filled={filled} cells={CELLS} label="Saving" className="h-3.5 gap-[3px]" />}
        action={
          <button type="button" disabled className={buttonClass("primary", "px-4 py-3 text-[10px]")}>
            SAVING
          </button>
        }
      >
        Reading GitHub, scoring your class, writing your lore. <span aria-live="polite">Tip: {tip}</span>
      </StateCard>
    </dialog>
  );
}

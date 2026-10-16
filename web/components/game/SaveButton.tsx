"use client";

import { formatCountdown } from "@/lib/format";
import { buttonClass } from "@/components/ui/pixel-button";
import { useGameState } from "./GameState";

/** SAVE (POST /sync). Shows SAVING while it runs and RESTING m:ss during the cooldown. */
export function SaveButton({ className = "" }: { className?: string }) {
  const { save, saving, restSeconds } = useGameState();
  const resting = restSeconds > 0;
  const label = saving ? "SAVING" : resting ? `RESTING ${formatCountdown(restSeconds)}` : "SAVE";

  return (
    <button
      type="button"
      onClick={save}
      disabled={saving || resting}
      aria-label={resting ? `Save. Resting, ready in ${formatCountdown(restSeconds)}` : undefined}
      className={buttonClass("primary", className)}
    >
      {label}
    </button>
  );
}

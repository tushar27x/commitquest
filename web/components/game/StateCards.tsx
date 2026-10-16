"use client";

import { StateCard } from "@/components/ui/StateCard";
import { buttonClass } from "@/components/ui/pixel-button";
import { loginUrl } from "@/lib/auth";
import { formatCountdown } from "@/lib/format";
import { useGameState } from "./GameState";

const ACTION = "px-4 py-3 text-[10px]";

/** 404 on insights: no character yet. ROLL CHARACTER runs the first save. */
export function NewGameCard() {
  const { save, saving, restSeconds } = useGameState();
  const resting = restSeconds > 0;
  return (
    <StateCard
      tone="gold"
      title="NEW GAME"
      titleId="new-game-title"
      action={
        <button type="button" onClick={save} disabled={saving || resting} className={buttonClass("primary", ACTION)}>
          {resting ? `RESTING ${formatCountdown(restSeconds)}` : "ROLL CHARACTER"}
        </button>
      }
    >
      {resting
        ? "No character yet, and the login save already ran. Roll one when the rest timer runs out."
        : "No character yet. Rolling one runs your first save. If the login save already ran, this shows the rest timer instead."}
    </StateCard>
  );
}

/** 409 on sync: the GitHub token was revoked. The JWT is still fine, so the data stays. */
export function ReconnectBanner() {
  return (
    <StateCard
      tone="ember"
      title="YOUR GITHUB SEAL BROKE"
      titleId="reconnect-title"
      role="alert"
      action={
        <a href={loginUrl()} className={buttonClass("primary", ACTION)}>
          RECONNECT GITHUB
        </a>
      }
    >
      GitHub no longer accepts the stored token. Your session is fine: reconnect GitHub to keep saving.
    </StateCard>
  );
}

/** 502 / network: GitHub or the server isn't answering. Whatever was loaded stays on screen. */
export function RoadBlockedCard({ onRetry, retrying = false }: { onRetry: () => void; retrying?: boolean }) {
  return (
    <StateCard
      tone="ember"
      title="THE ROAD IS BLOCKED"
      titleId="road-blocked-title"
      role="alert"
      action={
        <button type="button" onClick={onRetry} disabled={retrying} className={buttonClass("primary", ACTION)}>
          {retrying ? "RETRYING" : "RETRY"}
        </button>
      }
    >
      GitHub or the server is not answering. Your last save is safe and still on screen.
    </StateCard>
  );
}

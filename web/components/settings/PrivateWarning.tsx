"use client";

import { useEffect, useRef } from "react";
import { buttonClass } from "@/components/ui/pixel-button";
import type { PrivateAccess } from "@/lib/api/types";
import { canConfirm, type WarningState } from "@/lib/private";

/** The warning copy (contract B11, "Settings copy for the private switch"). Exported for tests. */
export const WARNING_COPY = {
  on: {
    title: "SHARE PRIVATE REPOS?",
    points: [
      "GitHub only offers one all-or-nothing permission, repo: read AND write access to all your private repos. We only read, and never write.",
      "We read commit messages (the latest few), README text, the names of files and folders in the repo root, and activity counts. Never your source code or diffs.",
      "Your private work will count in your totals, hero and insights. No private repo name, README or message is ever shown publicly, on the Arena, or sent to the AI.",
      "Turning this on resyncs your insights. It can take up to 90 seconds: keep this tab open.",
      "After this change you can't change it again for 7 days.",
    ],
    /** GitHub hasn't shared the scope yet: the switch only records the wish, then GitHub asks. */
    needsGithub: "First GitHub asks you to approve the permission above. Nothing private is read until you do.",
  },
  off: {
    title: "STOP SHARING PRIVATE REPOS?",
    points: [
      "Your private work is removed from your insights with a fresh resync. It can take up to 90 seconds: keep this tab open.",
      "You can't turn it back on for 7 days.",
      "Hero history you already earned stays: hearts, XP and levels are not rewound.",
      "Your streak and calendar come from GitHub's contribution calendar. If your GitHub profile has Include private contributions on, anonymous private counts can remain even when this is off. Change that on GitHub.",
    ],
  },
} as const;

export interface PrivateWarningProps {
  state: WarningState;
  access: PrivateAccess;
  onTick: (understood: boolean) => void;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * The confirm screen that stands between the SHARE PRIVATE REPOS switch and any change. A native modal
 * <dialog>: focus stays inside, the rest of the page is inert, Esc and the backdrop cancel.
 */
export function PrivateWarning(props: PrivateWarningProps) {
  // Mounted only while open, so showModal() runs when the dialog appears.
  return props.state.open ? <WarningDialog {...props} /> : null;
}

function WarningDialog({ state, access, onTick, onCancel, onConfirm }: PrivateWarningProps) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const el = dialog.current;
    if (el && !el.open) el.showModal();
    return () => el?.close();
  }, []);

  const kind = state.open ?? "on";
  const copy = WARNING_COPY[kind];
  const needsGithub = kind === "on" && access === "none";

  return (
    <dialog
      ref={dialog}
      aria-labelledby="private-warning-title"
      onCancel={(e) => {
        e.preventDefault();
        onCancel();
      }}
      onClick={(e) => {
        if (e.target === dialog.current) onCancel();
      }}
      className="m-auto max-h-[calc(100dvh-24px)] w-[calc(100%-32px)] max-w-[520px] overflow-y-auto bg-stone p-0 text-bone shadow-px-drop backdrop:bg-night/75"
    >
      <div className="flex flex-col gap-3 px-[18px] py-5 md:px-6">
        <h2 id="private-warning-title" className={`font-display text-[11px] leading-normal md:text-[13px] ${kind === "on" ? "text-gold" : "text-ember"}`}>
          {copy.title}
        </h2>
        <ul className="flex flex-col gap-2 text-[20px] leading-[1.05] text-muted">
          {copy.points.map((point) => (
            <li key={point} className="relative pl-4 before:absolute before:top-[0.45em] before:left-0 before:size-1.5 before:bg-gold before:content-['']">
              {point}
            </li>
          ))}
          {needsGithub && (
            <li className="relative pl-4 text-gold before:absolute before:top-[0.45em] before:left-0 before:size-1.5 before:bg-gold before:content-['']">
              {WARNING_COPY.on.needsGithub}
            </li>
          )}
        </ul>
        <label className="relative flex min-h-11 cursor-pointer items-center gap-3 text-[21px] leading-none text-bone">
          <input
            type="checkbox"
            checked={state.understood}
            onChange={(e) => onTick(e.target.checked)}
            className="peer size-6 shrink-0 cursor-pointer appearance-none bg-pedestal shadow-[0_0_0_2px_var(--color-outline)] checked:bg-gold checked:shadow-[0_0_0_2px_var(--color-outline),inset_0_0_0_6px_var(--color-outline)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-bone"
          />
          I understand
        </label>
        <div className="flex flex-wrap gap-4 pt-1">
          <button type="button" onClick={onCancel} autoFocus className={buttonClass("ghost", "px-4 py-3 text-[10px] shadow-[0_0_0_2px_var(--color-muted-2)]")}>
            CANCEL
          </button>
          <button type="button" onClick={onConfirm} disabled={!canConfirm(state)} className={buttonClass("primary", "px-4 py-3 text-[10px]")}>
            CONFIRM
          </button>
        </div>
      </div>
    </dialog>
  );
}

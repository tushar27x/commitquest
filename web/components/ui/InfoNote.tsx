"use client";

import { useId, useState } from "react";

export interface InfoNoteProps {
  /** Accessible name for the toggle button, e.g. "Why this might not match GitHub". */
  label: string;
  children: React.ReactNode;
}

/**
 * A small tap-to-reveal note next to a stat that can look surprising on its own.
 * No hover: the app is mobile-first, so the explanation opens on tap/click like everything else.
 */
export function InfoNote({ label, children }: InfoNoteProps) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  return (
    <span className="relative ml-1 inline-block">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={label}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex size-3.5 shrink-0 items-center justify-center rounded-full font-display text-[7px] text-muted-2 shadow-[0_0_0_1px_var(--color-outline)] hover:text-gold focus-visible:text-gold md:size-4 md:text-[8px]"
      >
        i
      </button>
      {open && (
        <span id={panelId} role="note" className="absolute top-full left-0 z-20 mt-1.5 block w-56 bg-stone p-2.5 font-body text-muted shadow-btn-flat md:w-64">
          {children}
        </span>
      )}
    </span>
  );
}

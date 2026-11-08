"use client";

import { useEffect, useRef, useState, useSyncExternalStore, type MouseEvent, type ReactNode } from "react";
import { StateHero } from "@/components/game/StateArt";
import { SegmentBar } from "@/components/ui/SegmentBar";
import { StateCard } from "@/components/ui/StateCard";
import { buttonClass } from "@/components/ui/pixel-button";
import { loginUrl } from "@/lib/auth";
import { useSession } from "@/lib/use-session";

// Every start button on the page shares one "starting" flag: the first click leaves for the
// backend login, and any further click (on any of them) is ignored, so a double click can't
// hit the login endpoint twice and trip its rate limit.
let starting = false;
let resetTimer: number | undefined;
const listeners = new Set<() => void>();

/** If the page is still here after this long, the trip failed: let the player try again. */
const GIVE_UP_MS = 20_000;

function setStarting(value: boolean) {
  starting = value;
  window.clearTimeout(resetTimer);
  if (value) resetTimer = window.setTimeout(() => setStarting(false), GIVE_UP_MS);
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  // Back from the login page (the browser may restore this page as it was).
  const onShow = (e: PageTransitionEvent) => {
    if (e.persisted) setStarting(false);
  };
  window.addEventListener("pageshow", onShow);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("pageshow", onShow);
  };
}

const useStarting = () =>
  useSyncExternalStore(
    subscribe,
    () => starting,
    () => false,
  );

export interface StartButtonProps {
  /** Replaces the default full-width PRESS START look. */
  className?: string;
  children?: ReactNode;
  /** Pressing Enter anywhere on the page also starts. Only one button on a page should set it. */
  enterStarts?: boolean;
}

/** PRESS START: to the backend login (or straight home with a live session). */
export function StartButton({ className = "w-full py-5 text-[14px]", children = "PRESS START", enterStarts = false }: StartButtonProps) {
  const link = useRef<HTMLAnchorElement>(null);
  const session = useSession();
  const busy = useStarting();
  // Only the button that was pressed shows the overlay (a page has several).
  const [pressed, setPressed] = useState(false);
  const href = session ? "/home" : loginUrl();

  useEffect(() => {
    if (!enterStarts) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Enter" || e.repeat || e.defaultPrevented) return;
      // Enter on a focused control already does its own thing.
      const target = e.target as HTMLElement | null;
      if (target && target !== document.body && target.closest("a, button, input, select, textarea")) return;
      link.current?.click();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [enterStarts]);

  const onClick = (e: MouseEvent<HTMLAnchorElement>) => {
    if (starting) {
      e.preventDefault();
      return;
    }
    setPressed(true);
    setStarting(true);
  };

  return (
    <>
      <a
        ref={link}
        href={href}
        onClick={onClick}
        aria-disabled={busy || undefined}
        className={`${buttonClass("start", className)} ${busy ? "pointer-events-none brightness-75" : ""}`}
      >
        {children}
      </a>
      {busy && pressed && <StartingOverlay />}
    </>
  );
}

/** Full-screen "OPENING THE GATE…" while the browser goes to the login. */
function StartingOverlay() {
  const dialog = useRef<HTMLDialogElement>(null);
  const [filled, setFilled] = useState(2);

  useEffect(() => {
    const el = dialog.current;
    if (el && !el.open) el.showModal();
    // Eases toward full and never claims to be done.
    const timer = window.setInterval(() => setFilled((n) => Math.min(11, n + 1)), 700);
    return () => {
      window.clearInterval(timer);
      el?.close();
    };
  }, []);

  return (
    <dialog
      ref={dialog}
      aria-labelledby="starting-title"
      // The trip can't be cancelled; the page gives up by itself if it doesn't happen.
      onCancel={(e) => e.preventDefault()}
      className="m-0 h-dvh max-h-none w-full max-w-none items-center justify-center bg-night/95 p-4 text-bone open:flex backdrop:bg-transparent"
    >
      <StateCard
        tone="gold"
        title="OPENING THE GATE…"
        titleId="starting-title"
        art={<StateHero />}
        role="status"
        className="w-full max-w-[420px]"
        meter={<SegmentBar filled={filled} cells={12} label="Loading" className="h-3.5 gap-[3px]" />}
      >
        Taking you to GitHub to sign in. This takes a moment.
      </StateCard>
    </dialog>
  );
}

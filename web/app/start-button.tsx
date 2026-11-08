"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { buttonClass } from "@/components/ui/pixel-button";
import { loginUrl } from "@/lib/auth";
import { useSession } from "@/lib/use-session";

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

  return (
    <a ref={link} href={href} className={buttonClass("start", className)}>
      {children}
    </a>
  );
}

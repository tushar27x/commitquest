"use client";

import { useEffect, useRef } from "react";
import { buttonClass } from "@/components/ui/pixel-button";
import { loginUrl } from "@/lib/auth";
import { useSession } from "@/lib/use-session";

/** PRESS START: to the backend login (or straight home with a live session). Enter also starts. */
export function StartButton() {
  const link = useRef<HTMLAnchorElement>(null);
  const session = useSession();
  const href = session ? "/home" : loginUrl();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Enter" || e.repeat || e.defaultPrevented) return;
      // Enter on a focused control already does its own thing.
      const target = e.target as HTMLElement | null;
      if (target && target !== document.body && target.closest("a, button, input, select, textarea")) return;
      link.current?.click();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <a
      ref={link}
      href={href}
      className={buttonClass("start", "w-full py-5 text-[14px]")}
    >
      PRESS START
    </a>
  );
}

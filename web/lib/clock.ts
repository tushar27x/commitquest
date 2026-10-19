"use client";

import { useEffect, useState } from "react";
import { clockNow } from "./mock";

// The story clock lives next to the rest of mock mode (lib/mock.ts).
export { clockNow };

/** The current time, refreshed every `everyMs`. Client components only. */
export function useNow(everyMs = 30_000): number {
  const [now, setNow] = useState(clockNow);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(clockNow()), everyMs);
    return () => window.clearInterval(timer);
  }, [everyMs]);
  return now;
}

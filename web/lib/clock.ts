"use client";

import { useEffect, useState } from "react";
import { MOCK_MODE } from "./mock";

/**
 * Mock mode plays the fixture story: it is Friday 16 Oct 2026, 18:20 in Asia/Kolkata
 * (plan §5), and the clock runs on from there. Otherwise it's the real time.
 */
const MOCK_STORY_NOW = Date.parse("2026-10-16T12:50:00Z");
const loadedAt = Date.now();

export function clockNow(): number {
  return MOCK_MODE ? MOCK_STORY_NOW + (Date.now() - loadedAt) : Date.now();
}

/** The current time, refreshed every `everyMs`. Client components only. */
export function useNow(everyMs = 30_000): number {
  const [now, setNow] = useState(clockNow);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(clockNow()), everyMs);
    return () => window.clearInterval(timer);
  }, [everyMs]);
  return now;
}

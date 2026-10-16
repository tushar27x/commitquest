"use client";

import { useHero, useInsights } from "@/lib/queries";

// Scaffold check: shows that the query layer answers (from fixtures in mock mode).
export function DataCheck() {
  const hero = useHero();
  const insights = useInsights();

  const line = (label: string, q: { isPending: boolean; error: { status: number } | null }, ok: string) =>
    `${label}: ${q.isPending ? "…" : q.error ? `error ${q.error.status}` : ok}`;

  return (
    <div className="w-full bg-stone p-4 text-left shadow-px">
      <p>{line("hero", hero, hero.data ? `${hero.data.class.current} LV ${hero.data.level}, ${hero.data.mood}` : "")}</p>
      <p>{line("insights", insights, insights.data ? insights.data.user_name : "")}</p>
    </div>
  );
}

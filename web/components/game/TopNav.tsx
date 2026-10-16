"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { formatClock } from "@/lib/format";
import { TABS, isActiveTab } from "@/lib/nav";
import { useHero, useInsights } from "@/lib/queries";
import { SaveButton } from "./SaveButton";

/** Desktop navigation (md and up): name, tabs, last sync time, SAVE. */
export function TopNav() {
  const pathname = usePathname();
  const insights = useInsights().data;
  const hero = useHero().data;
  const onHome = isActiveTab(pathname, "/home");

  return (
    <header className="sticky top-0 z-30 hidden bg-stone shadow-bar-down md:block">
      <nav
        aria-label="Main"
        className="mx-auto flex max-w-[1180px] flex-wrap items-center gap-x-[26px] gap-y-3 px-6 py-3.5 font-display text-[10px]"
      >
        <span className="text-gold">
          {insights?.user_name.toUpperCase() ?? "INSIGHT HUB"}
          {/* Home shows the level in its own panel. */}
          {!onHome && hero ? ` · LV ${hero.level}` : ""}
        </span>
        {TABS.map((tab) => {
          const active = isActiveTab(pathname, tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? "page" : undefined}
              className={
                active
                  ? "pb-1.5 text-gold shadow-[0_4px_0_var(--color-gold)]"
                  : "pb-1.5 text-muted-2 hover:text-gold active:text-gold"
              }
            >
              {tab.label}
            </Link>
          );
        })}
        <span className="flex-1" />
        {insights && <span className="text-muted-2">SYNCED {formatClock(insights.updated_at, hero?.timezone)}</span>}
        <SaveButton className="px-3.5 py-2.5 text-[10px]" />
      </nav>
    </header>
  );
}

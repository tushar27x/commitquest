"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { TABS, isActiveTab } from "@/lib/nav";
import { NAV_ICON, UI_ART } from "@/lib/art";

/** Phone navigation (below md): a bottom bar with 64 px tabs above the safe area. */
export function TabBar() {
  const pathname = usePathname();
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-5 bg-stone pb-[env(safe-area-inset-bottom)] shadow-bar-up md:hidden"
    >
      {TABS.map((tab) => {
        const active = isActiveTab(pathname, tab.href);
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={active ? "page" : undefined}
            className={`flex h-16 flex-col items-center justify-center gap-1.5 font-display text-[7px] ${
              active ? "bg-stone-lit text-gold shadow-[inset_0_3px_0_var(--color-gold)]" : "text-muted-2 active:text-gold"
            }`}
          >
            <span
              aria-hidden
              className="art nav-icon block"
              style={{ backgroundImage: `url(${UI_ART.navIcons})`, backgroundPosition: `${-24 * NAV_ICON[tab.href]}px ${active ? -24 : 0}px` }}
            />
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}

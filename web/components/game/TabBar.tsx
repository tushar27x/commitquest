"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { PixelSprite } from "@/components/PixelSprite";
import { TABS, isActiveTab } from "@/lib/nav";
import { tabIcon } from "@/lib/tab-icons";

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
            className={`flex h-16 flex-col items-center justify-center gap-[7px] font-display text-[7px] ${
              active ? "bg-stone-lit text-gold" : "text-muted-2 active:text-gold"
            }`}
          >
            <PixelSprite {...tabIcon(tab.href)} scale={2} />
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}

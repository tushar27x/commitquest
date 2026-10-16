import type { ReactNode } from "react";

/** The 56 px screen header on phones (each screen fills it differently). */
export function MobileHeader({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <header
      className={`sticky top-0 z-20 flex h-14 shrink-0 items-center gap-3 bg-stone px-4 pt-[env(safe-area-inset-top)] shadow-bar-down md:hidden ${className}`}
    >
      {children}
    </header>
  );
}

/** A plain title for the phone header (SETTINGS, JOURNAL…). */
export function MobileTitle({ children }: { children: ReactNode }) {
  return <h1 className="font-display text-[12px]">{children}</h1>;
}

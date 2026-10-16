import type { ReactNode } from "react";

/** The 56 px screen header on phones (each screen fills it differently). */
export function MobileHeader({
  children,
  className = "",
  flat = false,
}: {
  children: ReactNode;
  className?: string;
  /** No bottom edge: something sticky continues the header below it (Journal's tabs). */
  flat?: boolean;
}) {
  return (
    <header
      className={`sticky top-0 z-20 flex h-14 shrink-0 items-center gap-3 bg-stone px-4 pt-[env(safe-area-inset-top)] md:hidden ${flat ? "" : "shadow-bar-down"} ${className}`}
    >
      {children}
    </header>
  );
}

/** A plain title for the phone header (SETTINGS, JOURNAL…). */
export function MobileTitle({ children }: { children: ReactNode }) {
  return <h1 className="font-display text-[12px]">{children}</h1>;
}

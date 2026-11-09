import Link from "next/link";
import type { ReactNode } from "react";

/** The 56 px screen header on phones: a link back to the landing page, then what each screen puts in it. */
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
      <Link
        href="/"
        aria-label="Back to the landing page"
        className="-ml-1 flex h-9 w-7 shrink-0 items-center justify-center font-display text-[12px] text-muted-2 active:text-gold"
      >
        &lt;
      </Link>
      {children}
    </header>
  );
}

/** A plain title for the phone header (SETTINGS, JOURNAL…). */
export function MobileTitle({ children }: { children: ReactNode }) {
  return <h1 className="font-display text-[12px]">{children}</h1>;
}

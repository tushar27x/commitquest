import Link from "next/link";
import type { ReactNode } from "react";

/** The "<" at the left of a phone header: back to the landing page. */
export function BackToLanding() {
  return (
    <Link
      href="/"
      aria-label="Back to the landing page"
      className="-ml-1 flex h-9 w-7 shrink-0 items-center justify-center font-display text-[12px] text-muted-2 active:text-gold"
    >
      &lt;
    </Link>
  );
}

/**
 * The 56 px screen header on phones: a link back to the landing page, then what each screen puts in it.
 * A screen that lays its header out in balanced parts (Hero: level, title, CHANGE) passes `back={false}` and
 * puts <BackToLanding /> inside its own left part, so the title stays centred.
 */
export function MobileHeader({
  children,
  className = "",
  flat = false,
  back = true,
}: {
  children: ReactNode;
  className?: string;
  /** No bottom edge: something sticky continues the header below it (Journal's tabs). */
  flat?: boolean;
  /** Add the back-to-landing link before the children (default). */
  back?: boolean;
}) {
  return (
    <header
      className={`sticky top-0 z-20 flex h-14 shrink-0 items-center gap-3 bg-stone px-4 pt-[env(safe-area-inset-top)] md:hidden ${flat ? "" : "shadow-bar-down"} ${className}`}
    >
      {back && <BackToLanding />}
      {children}
    </header>
  );
}

/** A plain title for the phone header (SETTINGS, JOURNAL…). */
export function MobileTitle({ children }: { children: ReactNode }) {
  return <h1 className="font-display text-[12px]">{children}</h1>;
}

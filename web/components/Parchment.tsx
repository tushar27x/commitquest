import type { ReactNode } from "react";

/** Parchment holds AI-written text and nothing else (plan §5), so data and narrative never blur. */
export function Parchment({
  title,
  titleId,
  children,
  className = "",
}: {
  title?: ReactNode;
  titleId?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section aria-labelledby={titleId} className={`parchment flex flex-col gap-2 px-3.5 py-3 md:gap-2.5 md:px-[22px] md:py-5 ${className}`}>
      {title && (
        <h2 id={titleId} className="font-display text-[8px] leading-[1.6] text-parchment-heading md:text-[11px]">
          {title}
        </h2>
      )}
      {children}
    </section>
  );
}

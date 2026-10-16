import type { ReactNode } from "react";

/** Stand-in content until a screen's own step lands. */
export function Placeholder({ step, children }: { step: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 bg-stone p-5 shadow-px">
      <p className="font-display text-[9px] tracking-[1px] text-faint">COMING IN {step}</p>
      <p className="text-muted">{children}</p>
    </section>
  );
}

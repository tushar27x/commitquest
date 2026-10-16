import type { ReactNode } from "react";

export type StateTone = "gold" | "bone" | "ember";

const TITLE_TONE: Record<StateTone, string> = {
  gold: "text-gold",
  bone: "text-bone",
  ember: "text-ember",
};

export interface StateCardProps {
  title: ReactNode;
  tone: StateTone;
  children: ReactNode;
  /** The card's button or link. */
  action?: ReactNode;
  /** Extra content between the title and the copy (the saving bar). */
  meter?: ReactNode;
  /** `alert` for states that interrupt (banner), `status` for passive ones. */
  role?: "alert" | "status";
  titleId?: string;
  className?: string;
}

/** The system-state card from docs/design/png/system-states.png. */
export function StateCard({ title, tone, children, action, meter, role, titleId, className = "" }: StateCardProps) {
  return (
    <section
      role={role}
      aria-labelledby={titleId}
      className={`flex flex-col gap-3 bg-stone px-[22px] py-5 shadow-px-drop ${className}`}
    >
      <h2 id={titleId} className={`font-display text-[14px] leading-normal ${TITLE_TONE[tone]}`}>
        {title}
      </h2>
      {meter}
      <div className="text-[21px] leading-[1.08] text-muted">{children}</div>
      {action && <div className="mt-2 flex flex-wrap gap-4">{action}</div>}
    </section>
  );
}

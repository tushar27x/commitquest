export interface SegmentBarProps {
  /** Filled cells. */
  filled: number;
  cells: number;
  /** Accessible text, e.g. "140 of 350 XP". */
  label: string;
  fillClass?: string;
  emptyClass?: string;
  className?: string;
}

/** A segmented pixel bar (10–20 cells, 2–3 px gaps). */
export function SegmentBar({
  filled,
  cells,
  label,
  fillClass = "bg-gold",
  emptyClass = "bg-stone-lit",
  className = "h-2.5 gap-0.5",
}: SegmentBarProps) {
  const on = Math.max(0, Math.min(cells, Math.round(filled)));
  return (
    <div
      role="img"
      aria-label={label}
      className={`grid ${className}`}
      style={{ gridTemplateColumns: `repeat(${cells}, minmax(0, 1fr))` }}
    >
      {Array.from({ length: cells }, (_, i) => (
        <span key={i} className={i < on ? fillClass : emptyClass} />
      ))}
    </div>
  );
}

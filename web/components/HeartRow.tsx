import { UI_ART } from "@/lib/art";

/** Full (beating) and empty hearts from `ui/hearts.png`, with one accessible label ("2 of 3 hearts"). */
export function HeartRow({ lives, max = 3, className = "gap-1" }: { lives: number; max?: number; className?: string }) {
  return (
    <div role="img" aria-label={`${lives} of ${max} hearts`} className={`flex ${className}`}>
      {Array.from({ length: max }, (_, i) => (
        <span
          key={i}
          aria-hidden
          className={`art heart block ${i < lives ? "heart-full" : ""}`}
          // Each heart beats 0.3 s behind the one before it.
          style={{ backgroundImage: `url(${UI_ART.hearts})`, animationDelay: i < lives ? `${-0.3 * i}s` : undefined }}
        />
      ))}
    </div>
  );
}

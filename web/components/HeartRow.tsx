import { PixelSprite } from "@/components/PixelSprite";
import { iconSprite } from "@/lib/sprites";

const FULL = iconSprite("heart");
const EMPTY = iconSprite("heartEmpty");

/** Full and empty hearts with one accessible label ("2 of 3 hearts"). */
export function HeartRow({ lives, max = 3, scale = 3, className = "" }: { lives: number; max?: number; scale?: number; className?: string }) {
  return (
    <div role="img" aria-label={`${lives} of ${max} hearts`} className={`flex gap-1.5 ${className}`}>
      {Array.from({ length: max }, (_, i) => (
        <PixelSprite key={i} {...(i < lives ? FULL : EMPTY)} scale={scale} />
      ))}
    </div>
  );
}

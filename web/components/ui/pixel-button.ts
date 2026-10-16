// Class names for pixel buttons and button-styled links (no radius, hard shadow,
// pressed = shift into the shadow). Hover only applies under `@media (hover: hover)` in Tailwind 4.

export type ButtonVariant = "primary" | "start" | "ghost";

const BASE =
  "relative inline-flex items-center justify-center gap-2 border-0 font-display leading-none no-underline select-none " +
  "transition-none cursor-pointer disabled:cursor-not-allowed " +
  // Tap target: at least 44 px even when the button is drawn smaller.
  "after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:min-h-full after:-translate-y-1/2 after:content-['']";

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    "bg-gold text-ink shadow-btn hover:brightness-110 active:translate-x-[3px] active:translate-y-[3px] active:shadow-btn-flat " +
    "disabled:bg-stone-lit disabled:text-faint disabled:shadow-btn-flat disabled:brightness-100 disabled:translate-x-0 disabled:translate-y-0",
  // The big PRESS START: 4 px ring + 6 px drop.
  start:
    "bg-gold text-ink shadow-px-drop hover:brightness-110 active:translate-x-1.5 active:translate-y-1.5 active:shadow-px",
  ghost: "bg-transparent text-muted-2 hover:text-gold active:text-gold disabled:text-faint",
};

export function buttonClass(variant: ButtonVariant = "primary", extra = ""): string {
  return `${BASE} ${VARIANTS[variant]} ${extra}`.trim();
}

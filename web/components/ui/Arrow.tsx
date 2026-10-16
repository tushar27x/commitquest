/** A small pixel triangle in the text colour (the fonts have no ▶ ◀ glyphs). */
export function Arrow({ dir = "right", className = "" }: { dir?: "left" | "right"; className?: string }) {
  const shape =
    dir === "right"
      ? "border-y-[4px] border-l-[5px] border-y-transparent border-l-current"
      : "border-y-[4px] border-r-[5px] border-y-transparent border-r-current";
  return <span aria-hidden className={`inline-block size-0 ${shape} ${className}`} />;
}

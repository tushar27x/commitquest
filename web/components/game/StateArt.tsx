"use client";

import { useQueryClient } from "@tanstack/react-query";
import { HeroSprite } from "@/components/art/HeroSprite";
import { Portrait } from "@/components/art/Portrait";
import type { Archetype, Hero, Mood } from "@/lib/api/types";
import { CLASS_ORDER } from "@/lib/art";
import { queryKeys } from "@/lib/queries";

/**
 * The hero beside a system state, in the mood the state calls for. Reads the cached hero only
 * (never fetches): the states show while requests fail. Without one it's the Monk, like the boards.
 */
export function StateHero({ mood = "OKAY", archetype }: { mood?: Mood; archetype?: Archetype }) {
  const cached = useQueryClient().getQueryData<Hero>(queryKeys.hero)?.class.current;
  const cls = archetype ?? cached ?? "MONK";
  // ×1 is 96 px wide: on the narrowest phones it would squeeze the copy, so it hides below 360 px.
  return <HeroSprite archetype={cls} mood={mood} className="hidden min-[360px]:block" />;
}

/** NEW GAME: every class portrait in a row, the roll you're about to make. */
export function PortraitRoll() {
  return (
    <div aria-hidden className="flex max-w-full self-start overflow-hidden bg-[#1b1829] shadow-[0_0_0_2px_var(--color-outline)]">
      {CLASS_ORDER.map((archetype) => (
        <Portrait key={archetype} archetype={archetype} />
      ))}
    </div>
  );
}

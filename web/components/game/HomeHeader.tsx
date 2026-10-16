"use client";

import { SegmentBar } from "@/components/ui/SegmentBar";
import { useHero } from "@/lib/queries";
import { classAccent } from "@/lib/sprites";
import { MobileHeader } from "./MobileHeader";
import { SaveButton } from "./SaveButton";

const XP_CELLS = 14;

/** Home's phone header: level, XP bar, SAVE. */
export function HomeHeader() {
  const hero = useHero().data;
  return (
    <MobileHeader className="px-3">
      {hero && (
        <>
          <span className="font-display text-[10px]" style={{ color: classAccent(hero.class.current) }}>
            LV {hero.level}
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <SegmentBar
              filled={(hero.xp / hero.xp_for_next) * XP_CELLS}
              cells={XP_CELLS}
              label={`${hero.xp} of ${hero.xp_for_next} XP`}
            />
            <span aria-hidden className="text-[20px] leading-none text-muted-2">
              {hero.xp} / {hero.xp_for_next} XP
            </span>
          </div>
        </>
      )}
      <SaveButton className={`h-9 px-3 text-[9px] ${hero ? "" : "ml-auto"}`} />
    </MobileHeader>
  );
}

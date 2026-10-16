import type { Metadata } from "next";
import Link from "next/link";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Placeholder } from "@/components/game/Placeholder";
import { Screen } from "@/components/game/Screen";
import { Arrow } from "@/components/ui/Arrow";

export const metadata: Metadata = { title: "Change class · Insight Hub" };

const back = (
  <Link href="/hero" className="flex items-center gap-1.5 py-3 font-display text-[9px] text-gold">
    <Arrow dir="left" /> HERO
  </Link>
);

export default function Page() {
  return (
    <Screen
      title="CHANGE CLASS"
      actions={back}
      header={
        <MobileHeader className="justify-between">
          {back}
          <MobileTitle>CHANGE CLASS</MobileTitle>
          <span className="w-12" />
        </MobileHeader>
      }
    >
      <Placeholder step="U5">Pick any class you qualify for. You keep your level and hearts.</Placeholder>
    </Screen>
  );
}

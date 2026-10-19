import type { Metadata } from "next";
import Link from "next/link";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { ChangeClass } from "@/components/hero/ChangeClass";
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
      flush
      actions={back}
      header={
        <MobileHeader className="justify-between">
          {back}
          <MobileTitle>CHANGE CLASS</MobileTitle>
          <span className="w-12" />
        </MobileHeader>
      }
    >
      <ChangeClass />
    </Screen>
  );
}

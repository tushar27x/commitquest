import type { Metadata } from "next";
import Link from "next/link";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { ChangeClass } from "@/components/hero/ChangeClass";
import { Arrow } from "@/components/ui/Arrow";

// The game layout (GameShell) renders nothing on the server: the session lives in localStorage,
// so this page only ever renders in the browser and has no server UI to validate as instant.
export const instant = false;

export const metadata: Metadata = { title: "Change class · CommitQuest" };

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
        /* Its own BACK leads to Hero, so no second back link to the landing page here. */
        <MobileHeader back={false} className="justify-between">
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

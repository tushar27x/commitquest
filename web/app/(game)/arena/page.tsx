import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { ArenaView } from "@/components/arena/ArenaView";

export const metadata: Metadata = { title: "Arena · Insight Hub" };

export default function Page() {
  return (
    <Screen title="ARENA" flush header={<MobileHeader><MobileTitle>ARENA</MobileTitle></MobileHeader>}>
      <ArenaView />
    </Screen>
  );
}

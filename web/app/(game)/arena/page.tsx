import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Placeholder } from "@/components/game/Placeholder";
import { Screen } from "@/components/game/Screen";

export const metadata: Metadata = { title: "Arena · Insight Hub" };

export default function Page() {
  return (
    <Screen title="ARENA" header={<MobileHeader><MobileTitle>ARENA</MobileTitle></MobileHeader>}>
      <Placeholder step="U6">Leaderboards by class, after Phase 13.</Placeholder>
    </Screen>
  );
}

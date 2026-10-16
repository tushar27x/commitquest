import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Placeholder } from "@/components/game/Placeholder";
import { Screen } from "@/components/game/Screen";

export const metadata: Metadata = { title: "Hero · Insight Hub" };

export default function Page() {
  return (
    <Screen title="HERO" header={<MobileHeader><MobileTitle>HERO</MobileTitle></MobileHeader>}>
      <Placeholder step="U4">Your character sheet and class scores, then changing class (U5).</Placeholder>
    </Screen>
  );
}

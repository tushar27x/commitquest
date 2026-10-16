import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Placeholder } from "@/components/game/Placeholder";
import { Screen } from "@/components/game/Screen";

export const metadata: Metadata = { title: "Journal · Insight Hub" };

export default function Page() {
  return (
    <Screen title="JOURNAL" header={<MobileHeader><MobileTitle>JOURNAL</MobileTitle></MobileHeader>}>
      <Placeholder step="U4">Stats, repos and the AI reviews from your last 12 months.</Placeholder>
    </Screen>
  );
}

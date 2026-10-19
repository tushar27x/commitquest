import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { Settings } from "@/components/settings/Settings";

export const metadata: Metadata = { title: "Settings · Insight Hub" };

export default function Page() {
  return (
    <Screen title="SETTINGS" header={<MobileHeader><MobileTitle>SETTINGS</MobileTitle></MobileHeader>}>
      <Settings />
    </Screen>
  );
}

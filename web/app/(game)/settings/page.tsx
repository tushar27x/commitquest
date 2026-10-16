import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Placeholder } from "@/components/game/Placeholder";
import { Screen } from "@/components/game/Screen";

export const metadata: Metadata = { title: "Settings · Insight Hub" };

export default function Page() {
  return (
    <Screen title="SETTINGS" header={<MobileHeader><MobileTitle>SETTINGS</MobileTitle></MobileHeader>}>
      <Placeholder step="U5">Rest days, timezone, email alerts, the Arena toggle and log out.</Placeholder>
    </Screen>
  );
}

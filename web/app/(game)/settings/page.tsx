import type { Metadata } from "next";
import { MobileHeader, MobileTitle } from "@/components/game/MobileHeader";
import { Screen } from "@/components/game/Screen";
import { Settings } from "@/components/settings/Settings";

// The game layout (GameShell) renders nothing on the server: the session lives in localStorage,
// so this page only ever renders in the browser and has no server UI to validate as instant.
export const instant = false;

export const metadata: Metadata = { title: "Settings · Insight Hub" };

export default function Page() {
  return (
    <Screen title="SETTINGS" header={<MobileHeader><MobileTitle>SETTINGS</MobileTitle></MobileHeader>}>
      <Settings />
    </Screen>
  );
}

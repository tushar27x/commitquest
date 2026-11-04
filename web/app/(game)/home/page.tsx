import type { Metadata } from "next";
import { HomeHeader } from "@/components/game/HomeHeader";
import { Screen } from "@/components/game/Screen";
import { HomeView } from "@/components/home/HomeView";

// The game layout (GameShell) renders nothing on the server: the session lives in localStorage,
// so this page only ever renders in the browser and has no server UI to validate as instant.
export const instant = false;

export const metadata: Metadata = { title: "Home · Insight Hub" };

export default function Page() {
  return (
    <Screen title="HOME" hideTitle flush width="narrow" header={<HomeHeader />}>
      <HomeView />
    </Screen>
  );
}

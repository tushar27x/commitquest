import type { Metadata } from "next";
import { HomeHeader } from "@/components/game/HomeHeader";
import { Placeholder } from "@/components/game/Placeholder";
import { Screen } from "@/components/game/Screen";

export const metadata: Metadata = { title: "Home · Insight Hub" };

export default function Page() {
  return (
    <Screen title="HOME" header={<HomeHeader />}>
      <Placeholder step="U3">Tier backdrop, hero and mood, hearts, XP, the Today card, the week strip and recent events.</Placeholder>
    </Screen>
  );
}

import type { Metadata } from "next";
import { HomeHeader } from "@/components/game/HomeHeader";
import { Screen } from "@/components/game/Screen";
import { HomeView } from "@/components/home/HomeView";

export const metadata: Metadata = { title: "Home · Insight Hub" };

export default function Page() {
  return (
    <Screen title="HOME" hideTitle flush header={<HomeHeader />}>
      <HomeView />
    </Screen>
  );
}

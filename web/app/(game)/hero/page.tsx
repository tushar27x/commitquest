import type { Metadata } from "next";
import { HeroSheetScreen } from "@/components/hero/HeroSheet";

export const metadata: Metadata = { title: "Hero · Insight Hub" };

export default function Page() {
  return <HeroSheetScreen />;
}

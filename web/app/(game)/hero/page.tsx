import type { Metadata } from "next";
import { HeroSheetScreen } from "@/components/hero/HeroSheet";

// The game layout (GameShell) renders nothing on the server: the session lives in localStorage,
// so this page only ever renders in the browser and has no server UI to validate as instant.
export const instant = false;

export const metadata: Metadata = { title: "Hero · Insight Hub" };

export default function Page() {
  return <HeroSheetScreen />;
}

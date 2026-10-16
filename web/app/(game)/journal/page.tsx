import type { Metadata } from "next";
import { JournalScreen } from "@/components/journal/JournalScreen";

export const metadata: Metadata = { title: "Journal · Insight Hub" };

export default function Page() {
  return <JournalScreen />;
}

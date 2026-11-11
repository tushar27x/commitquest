import type { Metadata } from "next";
import { JournalScreen } from "@/components/journal/JournalScreen";

// The game layout (GameShell) renders nothing on the server: the session lives in localStorage,
// so this page only ever renders in the browser and has no server UI to validate as instant.
export const instant = false;

export const metadata: Metadata = { title: "Journal · CommitQuest" };

export default function Page() {
  return <JournalScreen />;
}

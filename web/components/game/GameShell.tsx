"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "@/lib/use-session";
import { GameStateProvider, useGameState } from "./GameState";
import { SavingOverlay } from "./SavingOverlay";
import { SessionEndedDialog } from "./SessionEndedDialog";
import { TabBar } from "./TabBar";
import { TopNav } from "./TopNav";

/** Layout for the tab screens. Without a session it sends the visitor to the title. */
export function GameShell({ children }: { children: ReactNode }) {
  const session = useSession();
  // The session lives in localStorage, so nothing renders on the server (session === null):
  // the game state (timers, storage reads) only ever mounts in the browser.
  if (session === null) return null;
  return (
    <GameStateProvider>
      <Guarded session={session}>{children}</Guarded>
    </GameStateProvider>
  );
}

function Guarded({ session, children }: { session: boolean; children: ReactNode }) {
  const router = useRouter();
  const { saving, sessionEnded } = useGameState();
  // After a 401 the token is gone, but the dialog decides when to leave.
  const allowed = session === true || sessionEnded;

  useEffect(() => {
    if (session === false && !sessionEnded) router.replace("/");
  }, [session, sessionEnded, router]);

  if (!allowed) return null;

  return (
    <div className="flex min-h-dvh flex-col">
      <TopNav />
      <main id="main" className="flex flex-1 flex-col pb-[calc(64px+env(safe-area-inset-bottom))] md:pb-0">
        {children}
      </main>
      <TabBar />
      {saving && <SavingOverlay />}
      {sessionEnded && <SessionEndedDialog />}
    </div>
  );
}

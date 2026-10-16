"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { StateCard } from "@/components/ui/StateCard";
import { buttonClass } from "@/components/ui/pixel-button";
import { useGameState } from "./GameState";

/** 401: the JWT is gone. The cached world stays readable behind it until the user leaves. */
export function SessionEndedDialog() {
  const dialog = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const { endSession } = useGameState();

  useEffect(() => {
    const el = dialog.current;
    if (el && !el.open) el.showModal();
    return () => el?.close();
  }, []);

  const backToTitle = () => {
    endSession();
    router.replace("/");
  };

  return (
    <dialog
      ref={dialog}
      aria-labelledby="session-ended-title"
      onCancel={(e) => e.preventDefault()}
      className="m-auto w-[calc(100%-32px)] max-w-[420px] overflow-visible bg-transparent p-0 text-bone backdrop:bg-night/70"
    >
      <StateCard
        tone="bone"
        title="SESSION ENDED"
        titleId="session-ended-title"
        action={
          <button type="button" onClick={backToTitle} autoFocus className={buttonClass("primary", "px-4 py-3 text-[10px]")}>
            BACK TO TITLE
          </button>
        }
      >
        Your 24-hour pass ran out. Offline, the cached world stays readable until you press SAVE.
      </StateCard>
    </dialog>
  );
}

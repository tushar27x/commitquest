"use client";

import { useSyncExternalStore } from "react";
import { hasSession, subscribeToken } from "./auth";

/** Whether the user has a usable session; null while rendering on the server. */
export function useSession(): boolean | null {
  return useSyncExternalStore(subscribeToken, hasSession, () => null);
}

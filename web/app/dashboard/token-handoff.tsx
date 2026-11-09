"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { hasSession, setToken, tokenFromFragment } from "@/lib/auth";
import { PRIVATE_RETURN_PATH, takePrivateReauth } from "@/lib/private";
import { sendBrowserZone } from "@/lib/queries";

/**
 * Stores the JWT from the fragment and leaves with `router.replace`, so the token never
 * stays in the address bar or in history. A fragment never reaches the server.
 * After a login it also sends the browser's zone if none is set yet (in the background).
 */
export function TokenHandoff() {
  const router = useRouter();
  const queryClient = useQueryClient();

  useEffect(() => {
    const token = tokenFromFragment(window.location.hash);
    if (token) {
      // A new login may be a different user: nothing cached may leak across.
      queryClient.clear();
      setToken(token);
      void sendBrowserZone(queryClient);
    }
    // A private re-auth (Settings) comes back to Settings, which reads the outcome (contract B11).
    const back = takePrivateReauth();
    router.replace(hasSession() ? (back ? PRIVATE_RETURN_PATH : "/home") : "/");
  }, [router, queryClient]);

  return null;
}

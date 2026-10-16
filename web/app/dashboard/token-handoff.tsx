"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { hasSession, setToken, tokenFromFragment } from "@/lib/auth";

/**
 * Stores the JWT from the fragment and leaves with `router.replace`, so the token never
 * stays in the address bar or in history. A fragment never reaches the server.
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
    }
    router.replace(hasSession() ? "/home" : "/");
  }, [router, queryClient]);

  return null;
}

import type { NextConfig } from "next";

// Wiring (I4, docs/api/contract.md "Conventions"): the web app calls /api/* on its own origin and
// this rewrite forwards it to the backend, so no CORS is needed. The login link is the exception
// (NEXT_PUBLIC_BACKEND_URL, lib/auth.ts): the OAuth state cookie must live on the backend origin.

/** Where /api/* goes. Server-side only, read when the config loads (`next dev`, `next build`). */
export const BACKEND_URL = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");

/**
 * How long the proxy waits for the backend. Next's default is 30 s, and on a timeout it answers a
 * plain-text 500 while the backend keeps going: a slow `POST /sync` (worst case about 90 s) would
 * show "road blocked" and then a 429, because the sync still claimed its cooldown.
 */
export const PROXY_TIMEOUT_MS = 120_000;

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  experimental: {
    proxyTimeout: PROXY_TIMEOUT_MS,
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` }];
  },
};

export default nextConfig;

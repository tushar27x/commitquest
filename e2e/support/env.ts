// Ports for the I5 servers. Unusual ones on purpose, so a developer's own `npm run dev` (3000)
// or backend (8080) is never reused by mistake.
export const MOCK_URL = "http://127.0.0.1:3201"; // next dev, NEXT_PUBLIC_MOCK=1
export const WEB_URL = "http://127.0.0.1:3202"; // next build + start, NEXT_PUBLIC_MOCK=0
export const BACKEND_URL = "http://127.0.0.1:18081"; // E2eBackend (Testcontainers, GitHub stubbed)
/** The browser's zone in the stubbed and full-stack projects: not UTC, so the zone hand-off has work to do. */
export const BROWSER_ZONE = "Europe/Berlin";

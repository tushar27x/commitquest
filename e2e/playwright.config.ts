// I5: Playwright e2e (docs/agents/WORKFLOW.md). Two runs, each on its own production build of web/
// (NEXT_PUBLIC_* is baked in at build time, and both builds use web/.next, so they run one after the other):
//   E2E_SUITE=mock - project `mock`: the build in mock mode. Every screen, mood, tier and the GET errors.
//   E2E_SUITE=web  - project `stubbed`: the real build with /api/* answered by Playwright from
//                    docs/api/fixtures/ (the #token hand-off, every error state of SAVE, change class,
//                    settings). Plus project `fullstack` with E2E_FULLSTACK=1: the same build against the
//                    real backend (E2eBackend: Testcontainers, GitHub stubbed, LLM unreachable; Docker needed).
// From e2e/: `npm test` runs both suites; `npm run test:mock`, `npm run test:web`, `npm run test:fullstack`.
// A production build, not `next dev`: StrictMode's double mount swallows `?mock_error` in dev (B38).
import { defineConfig, devices } from "@playwright/test";
import { BACKEND_URL, BROWSER_ZONE, MOCK_URL, WEB_URL } from "./support/env";

const CI = !!process.env.CI;
const SUITE = process.env.E2E_SUITE === "mock" ? "mock" : "web";
const FULLSTACK = SUITE === "web" && process.env.E2E_FULLSTACK === "1";
const desktop = { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 800 } };

export default defineConfig({
  testDir: "specs",
  forbidOnly: CI,
  retries: CI ? 1 : 0,
  workers: CI ? 2 : 4,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: { trace: "retain-on-failure" },
  projects:
    SUITE === "mock"
      ? [{ name: "mock", testDir: "specs/mock", use: { ...desktop, baseURL: MOCK_URL } }]
      : [
          { name: "stubbed", testDir: "specs/stubbed", use: { ...desktop, baseURL: WEB_URL, timezoneId: BROWSER_ZONE } },
          ...(FULLSTACK
            ? [{ name: "fullstack", testDir: "specs/fullstack", use: { ...desktop, baseURL: WEB_URL, timezoneId: BROWSER_ZONE } }]
            : []),
        ],
  webServer: SUITE === "mock" ? [
    {
      name: "web (mock build)",
      command: "npx next build && npx next start --port 3201 --hostname 127.0.0.1",
      cwd: "../web",
      url: MOCK_URL,
      env: { NEXT_PUBLIC_MOCK: "1" },
      reuseExistingServer: !CI,
      timeout: 300_000,
    },
  ] : [
    {
      name: "web (build)",
      // The rewrite target and the login link are baked in at build time.
      command: "npx next build && npx next start --port 3202 --hostname 127.0.0.1",
      cwd: "../web",
      url: WEB_URL,
      env: { NEXT_PUBLIC_MOCK: "0", NEXT_PUBLIC_BACKEND_URL: BACKEND_URL, BACKEND_URL },
      reuseExistingServer: !CI,
      timeout: 300_000,
    },
    ...(FULLSTACK
      ? [
          {
            name: "backend",
            command: [
              "./mvnw -B -q spring-boot:test-run",
              "-Dspring-boot.run.main-class=com.tushar27x.insightshub.it.E2eBackend",
              `-Dspring-boot.run.arguments="--server.port=18081 --e2e.backend-url=${BACKEND_URL} --e2e.frontend-url=${WEB_URL}"`,
            ].join(" "),
            cwd: "..",
            url: `${BACKEND_URL}/actuator/health`,
            reuseExistingServer: !CI,
            timeout: 600_000,
            stdout: "pipe" as const,
          },
        ]
      : []),
  ],
});

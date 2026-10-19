#!/usr/bin/env node
// I4: proves the Next rewrite (web/next.config.ts) forwards /api/* to the backend intact, and waits
// long enough for a slow POST /sync. Starts a fake backend and `next dev`, checks, and exits 1 on
// any failure. Run from the repo root: `node e2e/wiring/proxy-check.mjs` (needs web/node_modules).
//
// SYNC_DELAY_MS defaults to 35 s: past Next's 30 s default, so a missing proxyTimeout fails here.

import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { fileURLToPath } from "node:url";

const WEB = fileURLToPath(new URL("../../web/", import.meta.url));
const BACKEND_PORT = 18_080;
const WEB_PORT = 3_100;
const SYNC_DELAY_MS = Number(process.env.SYNC_DELAY_MS ?? 35_000);
const BASE = `http://127.0.0.1:${WEB_PORT}`;
// Not a real token (B31: nothing token-shaped in the repo); the fake backend only echoes it.
const AUTH = `Bearer wiring-check-${process.pid}`;

/** The fake backend: echoes what reached it, answers like the real one for the shapes that matter. */
const backend = createServer((req, res) => {
  let body = "";
  req.on("data", (chunk) => (body += chunk));
  req.on("end", () => {
    const seen = { method: req.method, url: req.url, authorization: req.headers.authorization ?? null, body };
    if (req.url === "/api/user/sync") {
      setTimeout(() => send(res, 200, "application/json", seen), SYNC_DELAY_MS);
    } else if (req.url === "/api/user/insights") {
      res.setHeader("Retry-After", "42");
      send(res, 429, "application/problem+json", {
        type: "about:blank", title: "Too Many Requests", status: 429, detail: "wiring check", instance: "/api/user/insights",
      });
    } else {
      send(res, 200, "application/json", seen);
    }
  });
});

function send(res, status, type, body) {
  res.writeHead(status, { "Content-Type": type });
  res.end(JSON.stringify(body));
}

const failures = [];
function check(ok, message) {
  console.log(`${ok ? "ok  " : "FAIL"} ${message}`);
  if (!ok) failures.push(message);
}

async function waitForWeb(timeoutMs = 120_000) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    try {
      await fetch(`${BASE}/api/health-probe`);
      return;
    } catch {
      await new Promise((r) => setTimeout(r, 500));
    }
  }
  throw new Error("next dev didn't start");
}

await new Promise((resolve) => backend.listen(BACKEND_PORT, "127.0.0.1", resolve));
const next = spawn("npx", ["next", "dev", "-p", String(WEB_PORT), "-H", "127.0.0.1"], {
  cwd: WEB,
  env: { ...process.env, BACKEND_URL: `http://127.0.0.1:${BACKEND_PORT}/`, NEXT_PUBLIC_MOCK: "0", NEXT_TELEMETRY_DISABLED: "1" },
  stdio: ["ignore", "inherit", "inherit"],
  detached: true,
});

try {
  await waitForWeb();

  // 1. Path, query string and Authorization reach the backend unchanged.
  const get = await fetch(`${BASE}/api/user/hero/events?days=7`, { headers: { Authorization: AUTH } });
  const seenGet = await get.json();
  check(get.status === 200, `GET is proxied (${get.status})`);
  check(seenGet.url === "/api/user/hero/events?days=7", `path and query kept (${seenGet.url})`);
  check(seenGet.authorization === AUTH, "Authorization header forwarded");

  // 2. A PUT body arrives intact.
  const prefs = { timezone: "Asia/Kolkata", rest_days: ["SUNDAY"], alerts_enabled: true, public_profile: false };
  const put = await fetch(`${BASE}/api/user/preferences`, {
    method: "PUT", headers: { Authorization: AUTH, "Content-Type": "application/json" }, body: JSON.stringify(prefs),
  });
  const seenPut = await put.json();
  check(seenPut.method === "PUT" && JSON.stringify(JSON.parse(seenPut.body)) === JSON.stringify(prefs), "PUT body forwarded");

  // 3. Error responses keep their status, Retry-After and problem+json type (the UI's countdown reads them).
  const limited = await fetch(`${BASE}/api/user/insights`, { headers: { Authorization: AUTH } });
  check(limited.status === 429, `429 passed through (${limited.status})`);
  check(limited.headers.get("retry-after") === "42", `Retry-After kept (${limited.headers.get("retry-after")})`);
  check((limited.headers.get("content-type") ?? "").startsWith("application/problem+json"), "problem+json kept");

  // 4. A sync slower than Next's 30 s default still comes back.
  const started = Date.now();
  const sync = await fetch(`${BASE}/api/user/sync`, { method: "POST", headers: { Authorization: AUTH } });
  const took = Math.round((Date.now() - started) / 1000);
  check(sync.status === 200, `POST /sync after ${took} s: ${sync.status} (needs experimental.proxyTimeout > ${SYNC_DELAY_MS / 1000} s)`);
} catch (err) {
  failures.push(String(err));
  console.error(err);
} finally {
  try {
    process.kill(-next.pid, "SIGTERM");
  } catch {}
  backend.close();
}

console.log(failures.length ? `\n${failures.length} check(s) failed` : "\nwiring OK");
process.exit(failures.length ? 1 : 0);

// "Install app" (PWA). Chrome and Edge on Android and desktop fire `beforeinstallprompt` once, early in
// the page's life, so a module-level listener (started from the providers) keeps the event until the user
// opens Settings. iOS Safari has no such event: the user adds the app by hand (Share, Add to Home Screen),
// so there Settings shows the steps instead of a button.
import { useSyncExternalStore } from "react";

/** What Settings shows: a button (`prompt`), the iOS steps (`ios`), or nothing. */
export type InstallMode = "prompt" | "ios" | "none";

/** The part of Chrome's `BeforeInstallPromptEvent` that we use (it isn't in lib.dom.d.ts). */
interface InstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

export interface InstallEnv {
  /** A deferred install prompt is waiting. */
  canPrompt: boolean;
  /** Already running as an installed app, or installed during this visit. */
  installed: boolean;
  ios: boolean;
}

/** Pure: installed apps show nothing; a waiting prompt wins; iOS gets the steps; the rest show nothing. */
export function resolveInstallMode({ canPrompt, installed, ios }: InstallEnv): InstallMode {
  if (installed) return "none";
  if (canPrompt) return "prompt";
  return ios ? "ios" : "none";
}

/** iPhone, iPod, iPad, and iPadOS (which reports itself as a Mac with a touch screen). */
export function isIos(userAgent: string, platform: string, maxTouchPoints: number): boolean {
  return /iphone|ipad|ipod/i.test(userAgent) || (platform === "MacIntel" && maxTouchPoints > 1);
}

let deferred: InstallPromptEvent | null = null;
let installedNow = false;
let started = false;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

/** Starts listening once, in the browser. Safe to call again (and on the server, where it does nothing). */
export function startInstallListener(): void {
  if (started || typeof window === "undefined") return;
  started = true;
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault(); // keep it for the INSTALL button instead of Chrome's mini-infobar
    deferred = event as InstallPromptEvent;
    emit();
  });
  window.addEventListener("appinstalled", () => {
    deferred = null;
    installedNow = true;
    emit();
  });
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function isStandalone(): boolean {
  return window.matchMedia("(display-mode: standalone)").matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

function currentMode(): InstallMode {
  return resolveInstallMode({
    canPrompt: deferred !== null,
    installed: installedNow || isStandalone(),
    ios: isIos(navigator.userAgent, navigator.platform, navigator.maxTouchPoints),
  });
}

/** The install mode for this browser, `none` on the server and until the first client render. */
export function useInstallMode(): InstallMode {
  return useSyncExternalStore(subscribe, currentMode, () => "none");
}

/** Shows the browser's install dialog. The event works once, so it is dropped either way. */
export async function promptInstall(): Promise<"accepted" | "dismissed" | "unavailable"> {
  const event = deferred;
  if (!event) return "unavailable";
  deferred = null;
  emit();
  await event.prompt();
  return (await event.userChoice).outcome;
}

"use client";

import { useState } from "react";
import { buttonClass } from "@/components/ui/pixel-button";
import { promptInstall, useInstallMode } from "@/lib/install";

const SECTION = "flex flex-col gap-2 bg-stone p-3.5 shadow-[0_0_0_3px_var(--color-outline)] md:p-5";
const HEADING = "font-display text-[9px] leading-normal md:text-[10px]";

/**
 * INSTALL APP: a button where the browser offers one (Android, desktop Chrome and Edge), the Share-menu
 * steps on iPhone and iPad, nothing when the app is already installed or the browser can't install it.
 */
export function InstallApp() {
  const mode = useInstallMode();
  const [dismissed, setDismissed] = useState(false);

  if (mode === "none") return null;
  return (
    <section aria-labelledby="install-title" className={SECTION}>
      <h2 id="install-title" className={HEADING}>
        INSTALL APP
      </h2>
      {mode === "prompt" ? (
        <>
          <p className="text-[18px] leading-none text-muted-2">Add Insight Hub to your home screen and open it like an app.</p>
          <button
            type="button"
            onClick={() => void promptInstall().then((outcome) => setDismissed(outcome === "dismissed"))}
            className={buttonClass("primary", "h-11 self-start px-4 text-[9px]")}
          >
            INSTALL
          </button>
          {dismissed && <p className="text-[18px] leading-none text-muted-2">No problem. You can install it later from your browser&apos;s menu.</p>}
        </>
      ) : (
        <>
          <p className="text-[18px] leading-none text-muted-2">Add Insight Hub to your home screen and open it like an app:</p>
          <ol className="m-0 flex list-decimal flex-col gap-1.5 pl-6 text-[20px] leading-none text-bone">
            <li>Tap the Share button in Safari.</li>
            <li>Choose &ldquo;Add to Home Screen&rdquo;.</li>
            <li>Tap Add.</li>
          </ol>
        </>
      )}
    </section>
  );
}

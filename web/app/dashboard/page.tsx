import type { Metadata } from "next";
import { TokenHandoff } from "./token-handoff";

export const metadata: Metadata = { title: "Entering · Insight Hub", robots: { index: false } };

/** The backend's login redirect lands here with `#token=<jwt>`. */
export default function DashboardPage() {
  return (
    <main className="flex min-h-dvh items-center justify-center">
      <p role="status" className="font-display text-[10px] text-muted-2">
        ENTERING THE REALM…
      </p>
      <TokenHandoff />
    </main>
  );
}

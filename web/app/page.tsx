import Link from "next/link";
import { DataCheck } from "./data-check";

// Placeholder until U2 builds the Title screen.
export default function Home() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-8 px-4 py-12 text-center">
      <h1 className="font-display text-[24px] leading-[1.3] text-gold [text-shadow:4px_4px_0_var(--color-outline)]">
        INSIGHT HUB
      </h1>
      <p className="text-muted">Keep your hero alive with your commits</p>
      <DataCheck />
      <Link href="/dev/sprites" className="font-display text-[10px] text-gold underline underline-offset-4">
        SPRITE KIT
      </Link>
    </main>
  );
}

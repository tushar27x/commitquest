import { PixelSprite } from "@/components/PixelSprite";
import type { Archetype } from "@/lib/api/types";
import { classSprite } from "@/lib/sprites";
import { StartButton } from "./start-button";

// Title screen (docs/design/png/title-phone.png). Scene colours come from
// docs/design/source/home-Title.dc.html; the art is CSS blocks, no images.

const PARTY: Archetype[] = ["PALADIN", "WIZARD", "BARD", "ROGUE"];

// [x, y, size] in the 390 px design frame.
const STARS = [
  [24, 30, 4], [70, 72, 2], [120, 24, 4], [168, 96, 2], [210, 40, 2], [250, 110, 4], [30, 150, 2], [350, 140, 2],
  [90, 200, 2], [330, 230, 4], [20, 290, 2], [150, 320, 2], [280, 300, 2], [370, 360, 2], [60, 370, 4], [200, 380, 2],
];

export default function TitlePage() {
  return (
    <main className="flex min-h-dvh flex-col bg-night">
      {/* The scene: sky bands across the full width, the art in a centred 390 px frame. */}
      <div className="relative h-[clamp(480px,72dvh,600px)] overflow-hidden bg-[linear-gradient(#0e0c16_0,#0e0c16_40%,#1a1630_40%,#1a1630_70%,#241e40_70%)]">
        <div className="absolute inset-y-0 left-1/2 w-[390px] -translate-x-1/2">
          {STARS.map(([x, y, size]) => (
            <span
              key={`${x},${y}`}
              aria-hidden
              className="absolute bg-bone"
              style={{ left: x, top: y, width: size, height: size }}
            />
          ))}
          <span aria-hidden className="absolute top-[52px] right-[50px] size-11 bg-parchment shadow-[inset_-12px_0_0_var(--color-parchment-border)]" />

          {/* Hills and the keep, anchored to the grass. */}
          <span
            aria-hidden
            className="absolute bottom-10 -left-5 h-[150px] w-[260px] bg-stone-lit [clip-path:polygon(0_100%,0_60%,15%_60%,15%_40%,35%_40%,35%_20%,60%_20%,60%_40%,80%_40%,80%_60%,100%_60%,100%_100%)]"
          />
          <span
            aria-hidden
            className="absolute bottom-10 left-[170px] h-[120px] w-[240px] bg-stone [clip-path:polygon(0_100%,0_70%,20%_70%,20%_45%,45%_45%,45%_30%,70%_30%,70%_50%,100%_50%,100%_100%)]"
          />
          <div aria-hidden className="absolute bottom-24 left-[236px] flex items-end">
            <span className="relative h-24 w-[26px] bg-[#3a3456] shadow-[inset_0_8px_0_var(--color-pedestal)]">
              <span className="absolute top-[26px] left-[9px] h-2.5 w-2 bg-gold" />
            </span>
            <span className="relative h-16 w-[52px] bg-[#322d4c]">
              <span className="absolute top-[30px] left-[18px] h-[34px] w-4 bg-night" />
            </span>
            <span className="relative h-28 w-[26px] bg-[#3a3456] shadow-[inset_0_8px_0_var(--color-pedestal)]">
              <span className="absolute top-[30px] left-[9px] h-2.5 w-2 bg-gold" />
            </span>
          </div>
          <div aria-hidden className="absolute inset-x-0 bottom-10 flex justify-center gap-3 min-[360px]:gap-[22px]">
            {PARTY.map((archetype) => (
              <PixelSprite key={archetype} {...classSprite(archetype)} scale={4} />
            ))}
          </div>
        </div>

        <div className="absolute inset-x-0 top-[clamp(56px,15dvh,128px)] flex flex-col items-center gap-[18px] px-4 text-center">
          <h1 className="font-display text-[30px] leading-[1.25] text-gold [text-shadow:4px_4px_0_#8a5a1a,8px_8px_0_var(--color-outline)] min-[360px]:text-[34px]">
            INSIGHT
            <br />
            HUB
          </h1>
          <p className="text-[26px] text-muted">Keep your hero alive with your commits</p>
        </div>

        <span
          aria-hidden
          className="absolute inset-x-0 bottom-0 h-10 bg-forest shadow-[inset_0_6px_0_var(--color-meadow-edge),inset_0_-4px_0_#1f4a28]"
        />
      </div>

      <div className="mx-auto flex w-full max-w-[390px] flex-col items-center gap-5 px-6 pt-9 pb-[max(28px,env(safe-area-inset-bottom))]">
        <StartButton />
        <p className="text-center text-[22px] leading-[1.1] text-muted">
          Sign in with GitHub. Insight Hub reads public activity only, never your private repos.
        </p>
        <p aria-hidden className="font-display text-[8px] text-faint">
          ENTER ALSO STARTS
        </p>
      </div>
    </main>
  );
}

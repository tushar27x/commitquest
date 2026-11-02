import { HeroSprite } from "@/components/art/HeroSprite";
import { PixelScene, Stars } from "@/components/art/SceneArt";
import type { Archetype } from "@/lib/api/types";
import { TITLE_SCENE } from "@/lib/art";
import { StartButton } from "./start-button";

// Title screen (docs/design/source/Title.dc.html): the castle scene (390×600 at ×1), anchored
// to the bottom so short screens crop the sky, with the party on the path.

const PARTY: Archetype[] = ["PALADIN", "WIZARD", "BARD", "ROGUE"];

// Beside the scene on wide screens: its edge colours, sky down to the grass line (130 px up).
const SIDES =
  "linear-gradient(to top, #0c2013 0, #2b5c37 80px, #2c5d39 125px, #59954a 125px 130px, #1b1735 130px, #221d42 200px, #291f4e 250px, #251c48 300px, #130f2d 400px, #0d0a1f 500px, #06040e 600px)";

// [x, y, delay in s] in the 390×600 scene.
const STARS = [[60, 90, 0], [150, 40, 1.4], [332, 150, 0.7], [240, 110, 2.2]] as const;

export default function TitlePage() {
  return (
    <main className="flex min-h-dvh flex-col bg-night">
      <div className="relative h-[clamp(480px,72dvh,600px)] overflow-hidden" style={{ background: SIDES }}>
        <PixelScene url={TITLE_SCENE.url} width={TITLE_SCENE.width} height={TITLE_SCENE.height} scale={1} mirrored={false}>
          <Stars stars={STARS} />
          <div aria-hidden className="absolute inset-x-0 top-[458px] flex justify-center">
            {PARTY.map((archetype) => (
              <HeroSprite key={archetype} archetype={archetype} className="-mx-[3px]" />
            ))}
          </div>
        </PixelScene>

        <div className="absolute inset-x-0 top-[clamp(56px,15dvh,128px)] flex flex-col items-center gap-4 px-4 text-center">
          <h1 className="font-display text-[30px] leading-[1.25] text-gold [text-shadow:4px_4px_0_#8a5a1a,8px_8px_0_var(--color-outline)] min-[360px]:text-[34px]">
            INSIGHT
            <br />
            HUB
          </h1>
          <p className="text-[26px] text-[#e6e0f4] [text-shadow:2px_2px_0_var(--color-outline)]">Keep your hero alive with your commits</p>
        </div>
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

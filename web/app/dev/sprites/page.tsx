import type { Metadata } from "next";
import type { ReactNode } from "react";
import { HeartRow } from "@/components/HeartRow";
import { HeroSprite } from "@/components/art/HeroSprite";
import { Portrait } from "@/components/art/Portrait";
import { HeroFigure } from "@/components/home/HeroFigure";
import type { Mood } from "@/lib/api/types";
import { CLASS_ORDER, NAV_ICON, UI_ART, classAccent } from "@/lib/art";
import { CLASS_INFO } from "@/lib/classes";
import { TABS } from "@/lib/nav";

export const metadata: Metadata = {
  title: "Art kit · CommitQuest",
  robots: { index: false },
};

const MOODS: Mood[] = ["OKAY", "HAPPY", "WEAK", "HUNGRY", "SLEEPING", "LEVEL_UP", "FAINTED"];
const CELL = "relative flex h-[156px] items-end justify-center overflow-hidden bg-[linear-gradient(#2a2540,#2a2540_76%,#1a1727_76%)] pb-3.5 shadow-btn-flat";

function Section({ title, note, children }: { title: string; note: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-display text-[14px] text-bone">{title}</h2>
        <p className="text-muted-2">{note}</p>
      </div>
      {children}
    </section>
  );
}

function Tag({ children }: { children: ReactNode }) {
  return <span className="font-display text-[9px] tracking-[1px] text-faint">{children}</span>;
}

/** Every piece of art from public/art/ (docs/design/art/), as the app draws it. */
export default function ArtKitPage() {
  return (
    <main className="mx-auto flex max-w-[1312px] flex-col gap-12 px-4 py-10 sm:px-8 md:px-16 md:py-14">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-3">
          <h1 className="font-display text-[20px] leading-[1.3] text-gold [text-shadow:4px_4px_0_var(--color-outline)] sm:text-[24px]">
            ART KIT
          </h1>
          <p className="text-[22px] text-muted sm:text-[26px]">Every sheet in docs/design/art, drawn by the app&apos;s components.</p>
        </div>
        <Tag>DEV · HI-RES ART</Tag>
      </header>

      <Section title="CLASSES" note="Idle loop at ×2, roster order">
        <ul className="grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {CLASS_ORDER.map((archetype) => (
            <li key={archetype} className="flex flex-col bg-stone shadow-px-drop">
              <div className="relative flex h-[290px] items-end justify-center bg-[linear-gradient(#2a2540,#2a2540_78%,#1a1727_78%)] pb-[30px]">
                <HeroSprite archetype={archetype} scale={2} label={`${archetype}, idle animation`} />
              </div>
              <div className="flex flex-col gap-2.5 px-5 pt-[18px] pb-[22px]">
                <h3 className="font-display text-[14px]" style={{ color: classAccent(archetype) }}>
                  {archetype}
                </h3>
                <p className="text-[22px] leading-[1.05]">{CLASS_INFO[archetype].habit}</p>
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="EVERY CLASS, EVERY MOOD" note="8 classes × 7 moods at ×1, as Home draws them">
        <div className="overflow-x-auto">
          <table className="min-w-[960px] border-separate border-spacing-2.5">
            <thead>
              <tr>
                <th />
                {MOODS.map((mood) => (
                  <th key={mood} scope="col" className="font-display text-[9px] font-normal text-muted">
                    {mood.replace("_", " ")}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {CLASS_ORDER.map((archetype) => (
                <tr key={archetype}>
                  <th scope="row" className="w-[120px] bg-[#1b1829] px-2.5 py-2 text-left font-normal shadow-btn-flat">
                    <Portrait archetype={archetype} />
                    <span className="font-display text-[8px]" style={{ color: classAccent(archetype) }}>
                      {archetype}
                    </span>
                  </th>
                  {MOODS.map((mood) => (
                    <td key={mood} className="p-0">
                      <div className={CELL}>
                        <HeroFigure archetype={archetype} mood={mood} scale={1} levelLabel="" hungryText="…" />
                      </div>
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section title="UI" note="Hearts, tab icons (idle and active), class portraits">
        <div className="flex flex-wrap items-center gap-8 bg-stone p-6 shadow-px">
          <HeartRow lives={2} max={3} className="gap-1.5" />
          <ul className="flex gap-3">
            {TABS.flatMap((tab) =>
              [0, -24].map((y) => (
                <li
                  key={`${tab.href}${y}`}
                  aria-label={`${tab.label}${y ? ", active" : ""}`}
                  className="art nav-icon"
                  style={{ backgroundImage: `url(${UI_ART.navIcons})`, backgroundPosition: `${-24 * NAV_ICON[tab.href]}px ${y}px` }}
                />
              )),
            )}
          </ul>
          <div className="flex">
            {CLASS_ORDER.map((archetype) => (
              <Portrait key={archetype} archetype={archetype} />
            ))}
          </div>
        </div>
      </Section>
    </main>
  );
}

import type { Metadata } from "next";
import type { ReactNode } from "react";
import { PixelSprite } from "@/components/PixelSprite";
import { CLASS_INFO } from "@/lib/classes";
import { CLASS_ORDER, classAccent, classSprite, iconSprite, type IconName } from "@/lib/sprites";

export const metadata: Metadata = {
  title: "Sprite kit · Insight Hub",
  robots: { index: false },
};

const SCALES = [2, 3, 4, 6, 8, 10];

const ICONS: { name: IconName; label: string }[] = [
  { name: "heart", label: "Heart" },
  { name: "heartEmpty", label: "Empty heart" },
  { name: "tree", label: "Tree" },
  { name: "sparkle", label: "Sparkle" },
  { name: "sweat", label: "Sweat drop" },
];

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

export default function SpritesPage() {
  const monk = classSprite("MONK");

  return (
    <main className="mx-auto flex max-w-[1312px] flex-col gap-12 px-4 py-10 sm:px-8 md:px-16 md:py-14">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-3">
          <h1 className="font-display text-[20px] leading-[1.3] text-gold [text-shadow:4px_4px_0_var(--color-outline)] sm:text-[24px]">
            SPRITE KIT
          </h1>
          <p className="text-[22px] text-muted sm:text-[26px]">
            Every sprite in docs/design/sprites.json, drawn by PixelSprite.
          </p>
        </div>
        <Tag>DEV · 16×16 SPRITES</Tag>
      </header>

      <Section title="CLASSES" note="×8 on a pedestal, roster order">
        <ul className="grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {CLASS_ORDER.map((archetype, i) => (
            <li key={archetype} className="flex flex-col bg-stone shadow-px-drop">
              <div className="h-2.5" style={{ background: classAccent(archetype) }} />
              <div className="relative flex h-44 items-end justify-center bg-[linear-gradient(#2a2540,#2a2540_70%,#1a1727_70%)] pb-3.5">
                <div className="flex flex-col items-center">
                  <PixelSprite {...classSprite(archetype)} scale={8} label={`${archetype} sprite`} />
                  <div aria-hidden className="mt-0.5 h-3 w-28 bg-pedestal shadow-[0_4px_0_var(--color-outline)]" />
                </div>
                <span className="absolute top-3.5 right-3.5">
                  <Tag>NO.{i + 1}</Tag>
                </span>
              </div>
              <div className="flex flex-col gap-2.5 px-5 pt-[18px] pb-[22px]">
                <h3 className="font-display text-[14px]" style={{ color: classAccent(archetype) }}>
                  {archetype}
                </h3>
                <p className="text-[22px] leading-[1.05]">{CLASS_INFO[archetype].habit}</p>
                <p className="leading-[1.1] text-muted-2">SCORED ON · {CLASS_INFO[archetype].signals}</p>
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="SCALES" note="Integer scales only: ×2 lists, ×3–4 small, ×6–10 hero">
        <ul className="flex flex-wrap items-end gap-8 bg-stone p-6 shadow-px">
          {SCALES.map((scale) => (
            <li key={scale} className="flex flex-col items-center gap-3">
              <PixelSprite {...monk} scale={scale} />
              <Tag>×{scale}</Tag>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="ICONS" note="×4 and ×8">
        <ul className="grid grid-cols-2 gap-8 sm:grid-cols-3 lg:grid-cols-6">
          {ICONS.map(({ name, label }) => (
            <li key={name} className="flex flex-col items-center gap-4 bg-stone p-5 shadow-px">
              <div className="flex h-16 items-end gap-4">
                <PixelSprite {...iconSprite(name)} scale={4} />
                <PixelSprite {...iconSprite(name)} scale={8} label={label} />
              </div>
              <Tag>{label.toUpperCase()}</Tag>
            </li>
          ))}
          <li className="flex flex-col items-center gap-4 bg-stone p-5 shadow-px">
            <div className="flex h-16 items-end gap-2" role="img" aria-label="2 of 3 hearts">
              <PixelSprite {...iconSprite("heart")} scale={4} />
              <PixelSprite {...iconSprite("heart")} scale={4} />
              <PixelSprite {...iconSprite("heartEmpty")} scale={4} />
            </div>
            <Tag>HEART ROW 2/3</Tag>
          </li>
        </ul>
      </Section>

      <Section title="VARIANTS" note="Sleeping eyes row (only MONK has one in the kit)">
        <ul className="flex flex-wrap gap-8 bg-stone p-6 shadow-px">
          <li className="flex flex-col items-center gap-3">
            <PixelSprite {...monk} scale={6} label="Monk, awake" />
            <Tag>AWAKE</Tag>
          </li>
          <li className="flex flex-col items-center gap-3">
            <PixelSprite {...classSprite("MONK", { sleeping: true })} scale={6} label="Monk, sleeping" />
            <Tag>SLEEPING</Tag>
          </li>
        </ul>
      </Section>
    </main>
  );
}

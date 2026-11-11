import { HeartRow } from "@/components/HeartRow";
import { StartButton } from "@/app/start-button";
import type { Archetype } from "@/lib/api/types";
import { classAccent, heroSheet } from "@/lib/art";
import { Backdrop } from "./Backdrop";
import { CLASSES, HOME_FEATURES, NAV_LINKS, PARTY, PILLARS, RULES, STEPS, TIERS, TRUST, WEEK, starField } from "./data";

// The landing page (the "Landing page" desktop and mobile boards): one responsive page, phone
// first. Cards are stone panels with the 4 px outline and a 6 px drop (`shadow-px-drop`).

const CARD = "bg-stone shadow-[0_0_0_4px_var(--color-outline),5px_5px_0_4px_var(--color-outline)] md:shadow-px-drop";
const H2 = "m-0 font-display text-[17px] leading-[1.5] text-gold [text-shadow:3px_3px_0_var(--color-outline)] md:text-[22px] md:leading-[1.4] md:[text-shadow:4px_4px_0_var(--color-outline)]";
const SECTION = "px-5 pt-14 pb-6 md:px-8 md:pt-20 md:pb-10";
const INNER = "mx-auto flex max-w-[1200px] flex-col gap-7 md:gap-10";
const GHOST_CTA = "bg-stone-lit text-bone px-[22px] py-4 text-[12px] min-h-[52px] w-full md:min-h-12 md:w-auto";

const STARS_FINALE = starField(26, 1920, 300, 33);

function Hero({ archetype, className, delay = 0 }: { archetype: Archetype; className?: string; delay?: number }) {
  return (
    <div
      aria-hidden
      className={`l-hero ${className ?? ""}`}
      style={{ backgroundImage: `url(${heroSheet(archetype).url})`, animationDelay: `${-delay}s` }}
    />
  );
}

/** A card with a coloured top bar, a pixel heading and VT323 body text. */
function AccentCard({ accent, title, body }: { accent: string; title: string; body: string }) {
  return (
    <article className={`flex flex-col ${CARD}`}>
      <div className="h-2 md:h-2.5" style={{ background: accent }} />
      <div className="flex flex-col gap-2.5 px-5 pt-5 pb-6 md:gap-3 md:px-6 md:pt-6 md:pb-7">
        <h3 className="m-0 font-display text-[12px] leading-[1.6] md:text-[13px]" style={{ color: accent }}>
          {title}
        </h3>
        <p className="m-0 text-[23px] leading-[1.1] md:text-[24px]">{body}</p>
      </div>
    </article>
  );
}

function Top() {
  return (
    <section className="relative overflow-hidden bg-[#0a0820] pb-11 md:pb-[68px]">
      <Backdrop />

      <nav aria-label="Primary" className="relative z-[3] mx-auto flex max-w-[1280px] flex-wrap items-center justify-between gap-x-7 gap-y-4 px-5 py-4 md:px-8 md:py-6">
        <a href="#top" className="py-3.5 font-display text-[12px] text-gold no-underline [text-shadow:3px_3px_0_var(--color-outline)] md:text-[14px]">
          COMMITQUEST
        </a>
        <div className="flex flex-wrap items-center gap-x-[22px] gap-y-1">
          {NAV_LINKS.map((l) => (
            <a key={l.href} href={l.href} className="hidden px-1 py-3.5 font-display text-[10px] text-muted no-underline hover:text-gold md:inline">
              {l.label}
            </a>
          ))}
          <StartButton className="min-h-11 px-3 py-3 text-[10px] md:px-[18px] md:py-3.5">PLAY</StartButton>
        </div>
      </nav>

      <div id="top" className="relative z-[2] mx-5 mt-3 flex flex-col md:mx-auto md:mt-0 md:max-w-[1280px] md:flex-row md:flex-wrap md:items-end md:gap-x-12 md:gap-y-8 md:px-8 md:pt-10">
        <div className="flex flex-col gap-[22px] bg-[rgba(10,8,26,0.84)] px-[18px] pt-6 pb-[26px] shadow-[0_0_0_4px_var(--color-outline),6px_6px_0_4px_var(--color-outline)] md:mb-28 md:min-w-0 md:flex-[1_1_460px] md:gap-[26px] md:px-8 md:pt-8 md:pb-[34px] md:shadow-[0_0_0_4px_var(--color-outline),8px_8px_0_4px_var(--color-outline)]">
          <p className="m-0 font-display text-[10px] leading-[1.6] tracking-[1px] text-class-cleric md:text-[11px]">A COMMIT-POWERED HABIT GAME</p>
          <h1 className="m-0 font-display text-[36px] leading-[1.25] text-gold [text-shadow:5px_5px_0_var(--color-outline)] md:text-[clamp(34px,5.6vw,64px)] md:[text-shadow:6px_6px_0_var(--color-outline)]">
            COMMIT
            <br />
            QUEST
          </h1>
          <p className="m-0 max-w-[560px] text-[32px] leading-[1.05] md:text-[34px]">Keep your hero alive with your commits.</p>
          <p className="m-0 max-w-[560px] text-[24px] leading-[1.1] text-muted md:text-[26px]">
            A tiny pixel hero lives on your GitHub activity. Code a little every day and it thrives. Go quiet for too long and it loses a level.
            <span className="hidden md:inline"> Your habits, turned into a game you actually want to check.</span>
          </p>
          <div className="flex flex-col gap-[18px] px-1 pt-1.5 md:flex-row md:flex-wrap md:gap-5 md:px-0">
            <StartButton className="min-h-[52px] w-full px-[22px] py-4 text-[12px] md:min-h-12 md:w-auto" enterStarts>
              PRESS START
            </StartButton>
            <a href="#play" className={`inline-flex items-center justify-center font-display leading-none no-underline shadow-px-drop hover:bg-[#3b3559] ${GHOST_CTA}`}>
              HOW IT WORKS
            </a>
          </div>
          <p className="m-0 text-[21px] text-muted-2 md:text-[22px]">Free to try. Sign in with GitHub. Reads public activity only.</p>
        </div>

        <div aria-hidden className="mt-7 flex flex-col items-center md:mx-auto md:mt-0 md:min-w-0 md:max-w-[620px] md:flex-[1_1_420px]">
          <div className="h-[330px] md:hidden" />
          <div className="relative z-[2] flex w-full flex-nowrap items-end justify-center gap-0.5 md:gap-1.5">
            {PARTY.map((archetype, i) => (
              <Hero key={archetype} archetype={archetype} delay={i * 1.1} className="min-w-0 flex-[0_1_88px] md:flex-[0_1_144px]" />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function What() {
  return (
    <section id="what" className={SECTION}>
      <div className={INNER}>
        <div className="flex max-w-[820px] flex-col gap-3.5 md:gap-4">
          <h2 className={H2}>WHAT IS COMMITQUEST?</h2>
          <p className="m-0 text-[27px] leading-[1.1] md:text-[30px]">
            CommitQuest reads your GitHub activity and turns it into a pet-style companion: a pixel hero whose health depends on whether you showed up to code today.
          </p>
          <p className="m-0 text-[24px] leading-[1.1] text-muted md:text-[26px]">
            It is a habit tracker with a heartbeat. Instead of a streak counter you forget about, you get a character you do not want to let down, plus a yearly Journal that explains what your coding really looks like.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-7 md:grid-cols-[repeat(auto-fit,minmax(300px,1fr))] md:gap-8">
          {PILLARS.map((p) => (
            <AccentCard key={p.title} {...p} />
          ))}
        </div>
      </div>
    </section>
  );
}

function HowToPlay() {
  return (
    <section id="play" className={SECTION}>
      <div className={INNER}>
        <h2 className={H2}>HOW TO PLAY</h2>
        <ol className="m-0 grid list-none grid-cols-1 gap-6 p-0 md:grid-cols-[repeat(auto-fit,minmax(250px,1fr))] md:gap-8">
          {STEPS.map((s, i) => (
            <li key={s.title} className={`flex gap-4 p-[18px] md:flex-col md:gap-3.5 md:p-6 ${CARD}`}>
              <span className="min-w-7 font-display text-[24px] text-gold md:text-[28px]">{i + 1}</span>
              <div className="flex flex-col gap-2 md:gap-3.5">
                <h3 className="m-0 font-display text-[11px] leading-[1.6] md:text-[12px] md:leading-[1.5]">{s.title}</h3>
                <p className="m-0 text-[23px] leading-[1.1] text-muted md:text-[24px]">{s.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function HomePreview() {
  return (
    <section id="home" className={SECTION}>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-[26px] md:flex-row md:flex-wrap md:items-center md:gap-12">
        <div className="flex flex-col gap-[26px] md:min-w-0 md:flex-[1_1_400px] md:gap-5">
          <h2 className={H2}>YOUR HOME SCREEN</h2>
          <p className="m-0 text-[26px] leading-[1.1] md:text-[28px]">Open the app and your hero is there, waiting. Everything you need to know today fits on one screen.</p>
          <ul className="m-0 hidden list-none flex-col gap-3.5 p-0 text-[24px] leading-[1.1] text-muted md:flex">
            {HOME_FEATURES.map((f) => (
              <li key={f.label}>
                <span className="font-display text-[11px]" style={{ color: f.color }}>{f.label}</span>
                {"  "}
                {f.text}
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-col items-center gap-3.5 md:min-w-0 md:flex-[1_1_420px]">
          <div className="flex w-full max-w-[440px] flex-col bg-stone shadow-[0_0_0_4px_var(--color-outline),6px_6px_0_4px_var(--color-outline)] md:shadow-[0_0_0_4px_var(--color-outline),8px_8px_0_4px_var(--color-outline)]">
            <div
              className="relative flex h-[270px] items-end justify-center overflow-hidden pb-2"
              style={{ background: "linear-gradient(#171233, #2c2158 86%, #3b2d68 86%)" }}
            >
              <div
                aria-hidden
                className="l-twinkle absolute top-0 left-0 size-[2px]"
                style={{ boxShadow: "30px 24px #fff, 90px 60px #fff, 180px 30px #fff, 260px 70px #fff, 330px 28px #fff, 390px 56px #fff, 60px 110px #fff, 360px 120px #fff" }}
              />
              <div aria-hidden className="absolute bottom-6 left-[22px] h-[22px] w-3.5 bg-[#8a5a32]" />
              <div aria-hidden className="l-glow absolute bottom-[46px] left-5 size-[18px] bg-gold shadow-[0_0_0_4px_rgba(242,182,50,0.25)]" />
              <Hero archetype="MONK" className="w-36 shrink-0" />
              <span className="absolute top-3 left-3 bg-outline px-2 py-1.5 font-display text-[9px] text-bone md:top-3.5 md:left-3.5 md:text-[10px]">CAMPFIRE</span>
            </div>
            <div className="flex flex-col gap-4 px-[18px] pt-[18px] pb-[22px] md:px-[22px] md:pt-5 md:pb-6">
              <div className="flex items-center justify-between gap-2.5">
                <span className="font-display text-[11px] text-gold md:text-[12px]">MONK · LV 3</span>
                <HeartRow lives={3} className="gap-1.5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-[20px] text-muted-2">
                  <span>XP</span>
                  <span>90 / 150</span>
                </div>
                <div className="box-border h-4 bg-outline p-[3px]">
                  <div className="h-full w-[60%] bg-gold" />
                </div>
              </div>
              <div className="flex flex-col gap-1 bg-stone-lit px-3.5 py-3 shadow-[0_0_0_3px_var(--color-outline)] md:px-4 md:py-3.5">
                <span className="font-display text-[10px] text-class-cleric">TODAY</span>
                <span className="text-[22px] leading-[1.1]">Not yet. Any contribution before midnight keeps your hearts.</span>
              </div>
              <div className="grid grid-cols-7 gap-1 md:gap-1.5">
                {WEEK.map((d) => (
                  <div key={d.day} className="flex flex-col items-center gap-1.5">
                    <span className="text-[18px] text-muted-2">{d.day}</span>
                    <div className="flex h-[30px] w-full items-center justify-center shadow-[0_0_0_2px_var(--color-outline)]" style={{ background: d.bg }}>
                      <span className="font-display text-[7px] md:text-[8px]" style={{ color: d.fg }}>{d.tag}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <p className="m-0 text-[20px] text-muted-2">Preview with sample data</p>
        </div>

        <ul className="m-0 flex list-none flex-col gap-3.5 p-0 text-[23px] leading-[1.1] text-muted md:hidden">
          {HOME_FEATURES.map((f) => (
            <li key={f.label}>
              <span className="font-display text-[10px]" style={{ color: f.color }}>{f.label}</span>
              {"  "}
              {f.text}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function Rules() {
  return (
    <section id="rules" className={SECTION}>
      <div className={INNER}>
        <div className="flex max-w-[760px] flex-col gap-3 md:gap-3.5">
          <h2 className={H2}>RULES OF THE REALM</h2>
          <p className="m-0 text-[25px] leading-[1.1] text-muted md:text-[26px]">Simple enough to remember, forgiving enough to keep playing. A rough week never deletes your hero.</p>
        </div>
        <div className="grid grid-cols-1 gap-[26px] md:grid-cols-[repeat(auto-fit,minmax(300px,1fr))] md:gap-7">
          {RULES.map((r) => (
            <AccentCard key={r.title} {...r} />
          ))}
        </div>
        <div className="flex flex-col gap-4 pt-1.5 md:gap-[18px] md:pt-0">
          <h3 className="m-0 font-display text-[12px] leading-[1.6] md:text-[13px]">YOUR WORLD GROWS WITH YOU</h3>
          <div className="grid grid-cols-2 gap-5 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))] md:gap-6">
            {TIERS.map((t) => (
              <div
                key={t.name}
                className="flex min-h-[92px] flex-col justify-end gap-1.5 p-3.5 shadow-[0_0_0_4px_var(--color-outline),4px_4px_0_4px_var(--color-outline)] md:min-h-24 md:gap-2 md:p-5 md:shadow-[0_0_0_4px_var(--color-outline),5px_5px_0_4px_var(--color-outline)]"
                style={{ background: t.bg }}
              >
                <span className="font-display text-[10px] text-bone [text-shadow:2px_2px_0_var(--color-outline)] md:text-[12px]">{t.name}</span>
                <span className="text-[21px] leading-none text-bone [text-shadow:2px_2px_0_var(--color-outline)] md:text-[22px]">{t.range}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function Classes() {
  return (
    <section id="classes" className={SECTION}>
      <div className={INNER}>
        <div className="flex max-w-[800px] flex-col gap-3 md:gap-3.5">
          <h2 className={H2}>EIGHT CLASSES, ONE HABIT EACH</h2>
          <p className="m-0 text-[24px] leading-[1.1] text-muted md:text-[26px]">
            CommitQuest scores all eight from your GitHub habits. Your highest score is your class, and you can switch to any class you qualify for (7-day cooldown). Level and hearts belong to the hero, so they come with you.
          </p>
          <p className="m-0 font-display text-[10px] leading-[1.6] text-class-cleric md:hidden">SWIPE TO MEET THEM ALL →</p>
        </div>
        <div className="l-rail" role="region" aria-label="Class cards, scroll sideways" tabIndex={0}>
          {CLASSES.map((c, i) => (
            <article key={c.archetype} className={`flex w-[260px] flex-none flex-col md:w-auto ${CARD}`}>
              <div className="h-2 md:h-2.5" style={{ background: classAccent(c.archetype) }} />
              <div
                className="relative flex h-56 items-end justify-center pb-1.5 md:h-[232px]"
                style={{ background: "linear-gradient(#2a2540, #2a2540 84%, #1a1727 84%)" }}
              >
                <Hero archetype={c.archetype} delay={i * 0.6} className="w-36 shrink-0" />
                <span className="absolute top-3 right-3 font-display text-[9px] text-muted-2 md:top-3.5 md:right-3.5">{c.num}</span>
              </div>
              <div className="flex flex-col gap-2 px-[18px] pt-4 pb-5 md:gap-2.5 md:px-5 md:pt-[18px] md:pb-[22px]">
                <h3 className="m-0 font-display text-[13px] md:text-[14px]" style={{ color: classAccent(c.archetype) }}>{c.archetype}</h3>
                <p className="m-0 min-h-[70px] text-[22px] leading-[1.05]">{c.habit}</p>
                <p className="m-0 text-[20px] leading-[1.1] text-muted-2">SCORED ON · {c.signals}</p>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function Journal() {
  return (
    <section id="journal" className={SECTION}>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-[22px] md:flex-row md:flex-wrap md:items-center md:gap-12">
        <div className="flex flex-col gap-[22px] md:min-w-0 md:flex-[1_1_400px] md:gap-5">
          <h2 className={H2}>THE JOURNAL</h2>
          <p className="m-0 text-[26px] leading-[1.1] md:text-[28px]">The Home screen tells you how you are doing today. The Journal tells you what your coding really looks like.</p>
          <ul className="m-0 flex list-none flex-col gap-2.5 p-0 text-[23px] leading-[1.1] text-muted md:gap-3 md:text-[24px]">
            <li>Yearly stats and a 28-day activity calendar</li>
            <li>Your top repos, each with an AI-written summary</li>
            <li>Three AI reviews of how you work</li>
            <li>Tips, like when your commit messages are getting lazy</li>
          </ul>
        </div>
        <div className="flex flex-col bg-parchment text-parchment-ink gap-2.5 p-5 shadow-[0_0_0_4px_var(--color-outline),inset_0_0_0_4px_var(--color-parchment-border),6px_6px_0_4px_var(--color-outline)] md:min-w-0 md:flex-[1_1_400px] md:px-6 md:py-[22px] md:shadow-[0_0_0_4px_var(--color-outline),inset_0_0_0_4px_var(--color-parchment-border),8px_8px_0_4px_var(--color-outline)]">
          <span className="font-display text-[9px] text-parchment-heading md:text-[10px]">THE ELDER&apos;S READING</span>
          <p className="m-0 text-[25px] leading-[1.1] md:text-[26px]">
            &ldquo;Sit, traveller. Your commits come in small, steady steps, and your messages say why, not only what. That is the mark of a Monk.&rdquo;
          </p>
          <span className="text-[20px] text-parchment-heading">Sample text. Every AI-written review lives on parchment.</span>
        </div>
      </div>
    </section>
  );
}

function Trust() {
  return (
    <section className={SECTION}>
      <div className="mx-auto grid max-w-[1200px] grid-cols-1 gap-6 md:grid-cols-[repeat(auto-fit,minmax(320px,1fr))] md:gap-8">
        {TRUST.map((t) => (
          <article key={t.title} className={`flex flex-col gap-2.5 p-5 md:gap-3 md:px-7 md:py-[26px] ${CARD}`}>
            <h3 className="m-0 font-display text-[11px] leading-[1.6] md:text-[13px] md:leading-[1.5]" style={{ color: t.accent }}>{t.title}</h3>
            <p className="m-0 text-[23px] leading-[1.1] md:text-[24px]">{t.body}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function FinalCta() {
  return (
    <section
      className="relative mt-14 overflow-hidden px-5 pt-[72px] md:mt-20 md:px-8 md:pt-24"
      style={{ background: "linear-gradient(#13111c, #1f1650 80%, #3a2a6b)" }}
    >
      <div aria-hidden className="l-twinkle absolute top-0 left-0 size-[2px]" style={{ boxShadow: STARS_FINALE }} />
      <div className="relative z-[2] mx-auto flex max-w-[760px] flex-col items-center gap-[22px] text-center md:gap-[26px]">
        <h2 className="m-0 font-display text-[20px] leading-[1.6] text-gold [text-shadow:4px_4px_0_var(--color-outline)] md:text-[clamp(22px,3.4vw,34px)] md:leading-[1.5] md:[text-shadow:5px_5px_0_var(--color-outline)]">
          YOUR HERO IS WAITING
        </h2>
        <p className="m-0 text-[26px] leading-[1.1] md:text-[28px]">Sign in, meet your class and make your first commit of the day. It takes less than a minute to start.</p>
        <StartButton className="min-h-[52px] self-stretch px-[22px] py-4 text-[12px] md:min-h-12 md:self-auto" />
      </div>
      <div aria-hidden className="relative z-[2] mt-10 grid grid-cols-4 md:mt-14 md:flex md:flex-wrap md:items-end md:justify-center md:gap-x-[18px] md:gap-y-1.5">
        {CLASSES.map((c, i) => (
          <div key={c.archetype} className="flex justify-center">
            <Hero archetype={c.archetype} delay={i * 0.6} className="w-[84px] shrink-0" />
          </div>
        ))}
      </div>
      <div
        aria-hidden
        className="h-9 bg-[#3b2d68] shadow-[inset_0_6px_0_var(--color-outline),inset_0_12px_0_#4a3a82] md:h-10"
      />
    </section>
  );
}

export function Landing() {
  return (
    <main className="landing overflow-hidden bg-night">
      <Top />
      <What />
      <HowToPlay />
      <HomePreview />
      <Rules />
      <Classes />
      <Journal />
      <Trust />
      <FinalCta />
      <footer className="flex flex-col flex-wrap justify-between gap-2 bg-outline px-5 py-6 text-[20px] text-muted-2 md:flex-row md:gap-x-6 md:gap-y-3 md:px-8 md:py-7">
        <span className="font-display text-[11px] text-gold">COMMITQUEST</span>
        <span>Keep your hero alive with your commits.</span>
      </footer>
    </main>
  );
}

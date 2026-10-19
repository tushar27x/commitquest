import { Block, GROUND, Ground, Stars, u } from "@/components/home/Backdrop";
import { PixelSprite } from "@/components/PixelSprite";
import { classAccent, classSprite } from "@/lib/sprites";

// The Arena while Phase 13 is being built: a half-finished arena wall, scaffolding and the
// Artificer at work. Same technique as the tier backdrops (stepped gradients and blocks, no
// images). Deliberately no padlock: padlocks mean "unlock it by playing", and this isn't that.

const at = (x: number) => `calc(50% + ${u(x)})`;
const above = (y: number) => `calc(${GROUND} + ${u(y)})`;

const SKY = "linear-gradient(#2a2350 0, #2a2350 34%, #43356e 34%, #43356e 58%, #6e4f7e 58%, #6e4f7e 72%, #b8705e 72%)";
const STONE = "#8a7f9e";
const STONE_LIT = "#a69cbb";
const STONE_DARK = "#2a2440";
const WOOD = "#8a5a32";
const PLANK = "#b07a3c";
/** An arch with a stepped top: no curves in pixel art. */
const ARCH = "polygon(25% 0, 75% 0, 75% 12%, 100% 12%, 100% 100%, 0 100%, 0 12%, 25% 12%)";
const FLAG = "polygon(0 0, 100% 50%, 0 100%)";

function Wall() {
  return (
    <>
      {/* The finished part: two rows of arches under battlements, pennants in class colours. */}
      <Block style={{ left: at(-172), bottom: GROUND, width: u(192), height: u(96), background: STONE, boxShadow: `inset 0 ${u(5)} 0 ${STONE_LIT}` }} />
      <Block style={{ left: at(-172), bottom: above(96), width: u(192), height: u(10), background: `repeating-linear-gradient(90deg, ${STONE} 0 ${u(14)}, transparent ${u(14)} ${u(22)})` }} />
      {[-158, -118, -78, -38].map((x) => (
        <Block key={x} style={{ left: at(x), bottom: GROUND, width: u(24), height: u(42), background: STONE_DARK, clipPath: ARCH }} />
      ))}
      {[-154, -114, -74, -34].map((x) => (
        <Block key={x} style={{ left: at(x), bottom: above(60), width: u(16), height: u(18), background: STONE_DARK, clipPath: ARCH }} />
      ))}
      {(["PALADIN", "BARBARIAN", "WIZARD"] as const).map((cls, i) => (
        <Block key={cls} style={{ left: at(-152 + i * 72), bottom: above(106), width: u(20), height: u(28) }}>
          <Block style={{ left: 0, bottom: 0, width: u(3), height: u(28), background: "#4a3420" }} />
          <Block style={{ left: u(3), top: 0, width: u(17), height: u(12), background: classAccent(cls), clipPath: FLAG }} />
        </Block>
      ))}
      {/* The unfinished part: a broken, stepped top. */}
      <Block style={{ left: at(20), bottom: GROUND, width: u(40), height: u(70), background: STONE, boxShadow: `inset 0 ${u(5)} 0 ${STONE_LIT}` }} />
      <Block style={{ left: at(60), bottom: GROUND, width: u(36), height: u(50), background: STONE, boxShadow: `inset 0 ${u(5)} 0 ${STONE_LIT}` }} />
      <Block style={{ left: at(96), bottom: GROUND, width: u(66), height: u(26), background: STONE, boxShadow: `inset 0 ${u(5)} 0 ${STONE_LIT}` }} />
    </>
  );
}

function Scaffold() {
  return (
    <>
      {[70, 146].map((x) => (
        <Block key={x} style={{ left: at(x), bottom: GROUND, width: u(5), height: u(130), background: WOOD }} />
      ))}
      <Block
        style={{
          left: at(75),
          bottom: above(64),
          width: u(71),
          height: u(56),
          background: WOOD,
          clipPath: "polygon(0 100%, 7% 100%, 100% 8%, 100% 0, 93% 0, 0 92%)",
        }}
      />
      {[64, 120].map((y) => (
        <Block key={y} style={{ left: at(62), bottom: above(y), width: u(96), height: u(6), background: PLANK, boxShadow: `0 ${u(3)} 0 #6b4226` }} />
      ))}
      {/* A stone on a rope, on its way up. */}
      <Block style={{ left: at(120), bottom: above(80), width: u(2), height: u(40), background: "#c9b48a" }} />
      <Block className="anim-bob" style={{ left: at(113), bottom: above(68), width: u(16), height: u(12), background: STONE_LIT, ["--bob" as string]: u(-3), boxShadow: `inset 0 ${u(-3)} 0 ${STONE}` }} />
    </>
  );
}

function Sign() {
  return (
    <>
      <Block style={{ left: at(-128), bottom: GROUND, width: u(6), height: u(40), background: "#6b4226" }} />
      <Block
        className="flex flex-col items-center justify-center bg-[#c9a46a] text-center font-display text-[7px] leading-[1.6] text-parchment-ink shadow-[0_0_0_2px_var(--color-outline)] md:text-[10px]"
        style={{ left: at(-170), bottom: above(30), width: u(90), height: u(34) }}
      >
        <span>UNDER</span>
        <span>CONSTRUCTION</span>
      </Block>
      {/* A pile of cut stones by the unfinished wall. */}
      <Block style={{ left: at(52), bottom: GROUND, width: u(22), height: u(14), background: STONE_LIT, boxShadow: `0 0 0 ${u(2)} ${STONE_DARK}` }} />
      <Block style={{ left: at(76), bottom: GROUND, width: u(22), height: u(14), background: STONE, boxShadow: `0 0 0 ${u(2)} ${STONE_DARK}` }} />
      <Block style={{ left: at(64), bottom: above(16), width: u(22), height: u(14), background: STONE_LIT, boxShadow: `0 0 0 ${u(2)} ${STONE_DARK}` }} />
    </>
  );
}

/** The construction scene (phone 292 px, desktop 520 px), like the Home hero scene. */
export function ArenaScene({ className = "" }: { className?: string }) {
  return (
    <section
      aria-label="The Arena, under construction"
      className={`relative h-[292px] min-w-0 overflow-hidden [--u:1] md:h-[520px] md:shadow-px-drop md:[--u:1.65] ${className}`}
    >
      <div aria-hidden className="absolute inset-0" style={{ background: SKY }}>
        <Stars stars={[[10, 10, 3], [34, 18, 2], [58, 8, 3], [80, 14, 2], [92, 26, 3]]} />
        <Wall />
        <Scaffold />
        <Ground fill="var(--color-meadow)" edge="var(--color-meadow-edge)" checker />
        <Sign />
      </div>
      <p className="sr-only">A half-built arena with scaffolding. An Artificer is building it.</p>
      {/* The builder: the Artificer, on the path in front of the gate. */}
      <div aria-hidden className="absolute bottom-[16%] left-1/2 -translate-x-1/2 md:hidden">
        <div className="anim-bob" style={{ ["--bob" as string]: "-6px" }}>
          <PixelSprite {...classSprite("ARTIFICER")} scale={6} />
        </div>
      </div>
      <div aria-hidden className="absolute bottom-[17%] left-1/2 hidden -translate-x-1/2 md:block">
        <div className="anim-bob" style={{ ["--bob" as string]: "-8px" }}>
          <PixelSprite {...classSprite("ARTIFICER")} scale={8} />
        </div>
      </div>
    </section>
  );
}

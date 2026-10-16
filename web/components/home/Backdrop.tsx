import type { CSSProperties, ReactNode } from "react";
import type { Tier } from "@/lib/api/types";

// Tier backdrops (plan §5, docs/design/source/home-Tiers.dc.html and home-Home*.dc.html):
// stepped hard-stop gradients and clip-path blocks, no images. Sizes are in `--u` units
// (1 on phone, larger on desktop, set by HeroScene), so one markup serves both scenes.
// The art keeps the board's literal colours: they are scene paint, not UI tokens.

const u = (n: number) => `calc(var(--u) * ${n}px)`;

/** Ground and path height; the hero stands just above the ground line. */
export const GROUND = "23%";

function Block({ style, className = "", children }: { style: CSSProperties; className?: string; children?: ReactNode }) {
  return (
    <div className={`absolute ${className}`} style={style}>
      {children}
    </div>
  );
}

const TRIANGLE = "polygon(50% 0, 100% 100%, 0 100%)";

function Ground({ fill, edge, checker }: { fill: string; edge: string; checker?: boolean }) {
  return (
    <>
      <Block
        style={{
          left: 0,
          right: 0,
          bottom: 0,
          height: GROUND,
          backgroundColor: fill,
          backgroundImage: checker
            ? "conic-gradient(var(--color-meadow-alt) 90deg, var(--color-meadow) 90deg 180deg, var(--color-meadow-alt) 180deg 270deg, var(--color-meadow) 270deg)"
            : undefined,
          backgroundSize: `${u(24)} ${u(24)}`,
          boxShadow: `inset 0 ${u(5)} 0 ${edge}`,
        }}
      />
      {/* The sand path the hero stands on. */}
      <Block
        style={{
          left: "50%",
          bottom: 0,
          width: u(84),
          height: GROUND,
          marginLeft: u(-42),
          background: "#c9a46a",
          boxShadow: `inset ${u(5)} 0 0 #a8844e, inset ${u(-5)} 0 0 #a8844e`,
        }}
      />
    </>
  );
}

function Stars({ stars }: { stars: [number, number, number][] }) {
  return stars.map(([x, y, size]) => (
    <Block key={`${x},${y}`} style={{ left: `${x}%`, top: `${y}%`, width: size, height: size, background: "var(--color-bone)" }} />
  ));
}

function Campfire() {
  return (
    <>
      <Stars stars={[[8, 12, 3], [30, 22, 3], [52, 9, 4], [70, 30, 3], [88, 16, 3], [18, 40, 2], [62, 44, 2]]} />
      <Ground fill="#2a5a2e" edge="#3f7a3c" />
      {/* Tent */}
      <Block style={{ left: `calc(50% - ${u(170)})`, bottom: GROUND, width: u(110), height: u(80), background: "#7a4f28", clipPath: TRIANGLE }} />
      <Block style={{ left: `calc(50% - ${u(126)})`, bottom: GROUND, width: u(22), height: u(36), background: "#2a1d12", clipPath: TRIANGLE }} />
      {/* Fire: logs, flame, core, spark */}
      <Block style={{ left: `calc(50% + ${u(64)})`, bottom: `calc(${GROUND} - ${u(6)})`, width: u(44), height: u(10), background: "#6b4226", boxShadow: `0 ${u(-4)} 0 #4a2e18` }} />
      <Block className="anim-flicker" style={{ left: `calc(50% + ${u(76)})`, bottom: `calc(${GROUND} + ${u(4)})`, width: u(20), height: u(26), background: "#e0603a" }}>
        <Block style={{ left: u(4), bottom: u(4), width: u(12), height: u(16), background: "var(--color-gold)" }} />
      </Block>
      <Block className="anim-twinkle" style={{ left: `calc(50% + ${u(84)})`, bottom: `calc(${GROUND} + ${u(36)})`, width: u(4), height: u(6), background: "#e0603a" }} />
    </>
  );
}

function House({ width, height, roof, wall, wallEdge, windows, door }: {
  width: number;
  height: number;
  roof: string;
  wall: string;
  wallEdge: string;
  windows: number[];
  door?: number;
}) {
  const roofH = Math.round(height * 0.4);
  return (
    <>
      <Block style={{ left: u(-6), top: 0, width: u(width + 12), height: u(roofH), background: roof, clipPath: TRIANGLE }} />
      <Block style={{ left: 0, top: u(roofH), width: u(width), height: u(height - roofH), background: wall, boxShadow: `inset 0 ${u(4)} 0 ${wallEdge}` }} />
      {windows.map((x) => (
        <Block key={x} style={{ left: u(x), top: u(roofH + 12), width: u(15), height: u(13), background: "var(--color-gold)", boxShadow: `0 0 0 ${u(2)} #4a3420` }} />
      ))}
      {door !== undefined && <Block style={{ left: u(door), top: u(roofH + 22), width: u(20), height: u(height - roofH - 22), background: "#4a3420" }} />}
    </>
  );
}

function Village() {
  return (
    <>
      <Block style={{ right: "14%", bottom: "34%", width: u(28), height: u(28), background: "var(--color-gold)", boxShadow: `0 0 0 ${u(5)} rgba(242, 182, 50, 0.25)` }} />
      <Block
        style={{
          left: 0,
          right: 0,
          bottom: GROUND,
          height: u(56),
          background: "var(--color-stone-lit)",
          clipPath: "polygon(0 100%, 0 50%, 14% 50%, 14% 25%, 32% 25%, 32% 55%, 50% 55%, 50% 15%, 68% 15%, 68% 45%, 86% 45%, 86% 30%, 100% 30%, 100% 100%)",
        }}
      />
      <Block style={{ left: `max(${u(14)}, 7%)`, bottom: GROUND, width: u(84), height: u(92) }}>
        <House width={84} height={92} roof="#a8442e" wall="#8a6a4a" wallEdge="#6b4f36" windows={[12]} door={50} />
      </Block>
      <Block style={{ right: `max(${u(12)}, 6%)`, bottom: GROUND, width: u(72), height: u(80) }}>
        <House width={72} height={80} roof="#5a4a8a" wall="#9a7a56" wallEdge="#7a5e40" windows={[12, 44]} />
      </Block>
      {/* Fence: posts and two rails along the ground line. */}
      <Block
        style={{
          left: 0,
          right: 0,
          bottom: GROUND,
          height: u(14),
          background: `repeating-linear-gradient(90deg, #6b4226 0 ${u(4)}, transparent ${u(4)} ${u(18)}), linear-gradient(transparent 0 ${u(3)}, #6b4226 ${u(3)} ${u(5)}, transparent ${u(5)} ${u(8)}, #6b4226 ${u(8)} ${u(10)}, transparent ${u(10)})`,
        }}
      />
      <Ground fill="var(--color-meadow)" edge="var(--color-meadow-edge)" checker />
    </>
  );
}

function Keep() {
  // The keep stands left of centre, so the hero waits in front of its gate.
  const at = (x: number) => `calc(50% + ${u(x - 230)})`;
  const above = (y: number) => `calc(${GROUND} + ${u(y)})`;
  return (
    <>
      <Ground fill="var(--color-meadow)" edge="var(--color-meadow-edge)" checker />
      <Block style={{ left: at(20), bottom: GROUND, width: u(220), height: u(70), background: "#8a84a3", boxShadow: `inset 0 ${u(6)} 0 #a29cbb` }} />
      <Block style={{ left: at(20), bottom: above(70), width: u(220), height: u(12), background: `repeating-linear-gradient(90deg, #8a84a3 0 ${u(16)}, transparent ${u(16)} ${u(26)})` }} />
      <Block style={{ left: at(92), bottom: GROUND, width: u(76), height: u(140), background: "#6d6a80", boxShadow: `inset ${u(6)} 0 0 #8a84a3` }} />
      <Block style={{ left: at(92), bottom: above(140), width: u(76), height: u(12), background: `repeating-linear-gradient(90deg, #6d6a80 0 ${u(14)}, transparent ${u(14)} ${u(22)})` }} />
      <Block style={{ left: at(120), bottom: above(84), width: u(20), height: u(24), background: "var(--color-night)" }} />
      <Block style={{ left: at(128), bottom: above(152), width: u(4), height: u(30), background: "#4a3420" }} />
      <Block className="anim-twinkle" style={{ left: at(132), bottom: above(166), width: u(24), height: u(14), background: "var(--color-gold)", animationDuration: "1.6s" }} />
      <Block style={{ left: at(112), bottom: GROUND, width: u(36), height: u(44), background: "#4a3420" }} />
    </>
  );
}

function Citadel() {
  const at = (x: number) => `calc(50% + ${u(x - 139)})`;
  const above = (y: number) => `calc(${GROUND} + ${u(y)})`;
  const tower = (x: number, w: number, h: number) => (
    <Block style={{ left: at(x), bottom: above(60), width: u(w), height: u(h), background: "#b3aac8", boxShadow: `inset ${u(5)} 0 0 #d2cbe2` }} />
  );
  return (
    <>
      <Ground fill="#4f8a3e" edge="#6fb35a" />
      <Block style={{ left: at(14), bottom: GROUND, width: u(250), height: u(60), background: "#c9c0dc", boxShadow: `inset 0 ${u(6)} 0 #e8e4f0` }} />
      {tower(24, 44, 90)}
      {tower(112, 54, 120)}
      {tower(210, 44, 90)}
      <Block style={{ left: at(18), bottom: above(150), width: u(56), height: u(34), background: "#5a4a8a", clipPath: TRIANGLE }} />
      <Block style={{ left: at(105), bottom: above(180), width: u(68), height: u(14), background: "#5a4a8a" }} />
      <Block style={{ left: at(204), bottom: above(150), width: u(56), height: u(34), background: "#5a4a8a", clipPath: TRIANGLE }} />
      <Block style={{ left: at(128), bottom: above(94), width: u(22), height: u(30), background: "var(--color-gold)" }} />
      <Block style={{ left: at(36), bottom: above(94), width: u(18), height: u(26), background: "var(--color-gold)" }} />
      <Block style={{ left: at(222), bottom: above(94), width: u(18), height: u(26), background: "var(--color-gold)" }} />
    </>
  );
}

const SKY: Record<Tier, string> = {
  CAMPFIRE: "linear-gradient(#0e0c16 0, #0e0c16 55%, #1a1630 55%)",
  VILLAGE: "linear-gradient(#241e40 0, #241e40 26%, #3a2f5c 26%, #3a2f5c 48%, #6b4a6e 48%, #6b4a6e 62%, #c8705a 62%, #c8705a 70%, #e8a86a 70%)",
  KEEP: "linear-gradient(#4a6aa8 0, #4a6aa8 40%, #6f8fc8 40%, #6f8fc8 70%, #9ab4e0 70%)",
  CITADEL: "linear-gradient(#e08a5a 0, #e08a5a 35%, #eda56a 35%, #eda56a 65%, #f6c87e 65%)",
};

const ART: Record<Tier, () => ReactNode> = { CAMPFIRE: Campfire, VILLAGE: Village, KEEP: Keep, CITADEL: Citadel };

/** Fills its (relative, overflow-hidden) parent with the tier's sky, scenery and ground. */
export function Backdrop({ tier }: { tier: Tier }) {
  const Art = ART[tier];
  return (
    <div aria-hidden className="absolute inset-0" style={{ background: SKY[tier] }}>
      <Art />
    </div>
  );
}

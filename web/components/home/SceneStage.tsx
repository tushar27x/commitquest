import type { CSSProperties, ReactNode } from "react";
import type { Tier } from "@/lib/api/types";
import { SCENE_LAYERS, type SceneNode } from "./scene-layers";

/** The stage the layers are drawn on (scene-layers.ts), in its own pixels. */
export const STAGE = { width: 960, height: 540 } as const;

/** Where the hero stands on the stage: a 192×256 box (the idle sheet at ×2), centred. */
const HERO_BOX: CSSProperties = { position: "absolute", left: 384, top: 250, width: 192, height: 256 };

function Layers({ nodes, hero }: { nodes: SceneNode[]; hero: ReactNode }) {
  return nodes.map((node, i) => {
    if (node.hero) {
      return (
        <div key={i} style={HERO_BOX}>
          {hero}
        </div>
      );
    }
    const Tag = node.t ?? "div";
    return (
      <Tag key={i} className={node.c} style={node.s}>
        {node.k && <Layers nodes={node.k} hero={hero} />}
      </Tag>
    );
  });
}

/**
 * A tier's animated backdrop: the 960×540 stage, scaled by its parent to the screen's height and
 * centred (a narrower screen crops the sides). `children` is the hero, drawn between the ground
 * and the foreground layers.
 */
export function SceneStage({ tier, children }: { tier: Tier; children: ReactNode }) {
  return (
    <div
      aria-hidden
      className="sc2 absolute bottom-0 left-1/2 origin-bottom scale-[0.5407] md:scale-[0.963]"
      style={{ width: STAGE.width, height: STAGE.height, marginLeft: -STAGE.width / 2 }}
    >
      <Layers nodes={SCENE_LAYERS[tier]} hero={children} />
    </div>
  );
}

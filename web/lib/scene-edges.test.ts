import { describe, expect, it } from "vitest";
import { edgeGradient } from "@/components/art/SceneArt";
import type { Tier } from "./api/types";
import { SCENE } from "./art";
import { SCENE_EDGES } from "./scene-edges";

const TIERS: Tier[] = ["CAMPFIRE", "VILLAGE", "KEEP", "CITADEL"];

describe("scene edges", () => {
  it.each(TIERS)("%s covers both sides of the phone and desktop art, bottom to top", (tier) => {
    for (const size of ["phone", "desktop"] as const) {
      for (const side of ["left", "right"] as const) {
        const { ground, above } = SCENE_EDGES[tier][size][side];
        expect(ground).toHaveLength(3);
        expect(above[0][1]).toBe(ground.at(-1)![1]);
        expect(above.at(-1)![1]).toBe(SCENE[size].height);
        const rows = [...ground, ...above].map(([, row]) => row);
        expect(rows).toEqual([...rows].sort((a, b) => a - b));
      }
    }
  });

  it("draws the ground as hard bands and the rest as soft stops, at the drawn scale", () => {
    const edge = { ground: [["#111111", 2], ["#222222", 3], ["#333333", 4]], above: [["#444444", 4], ["#555555", 10]] } as const;
    expect(edgeGradient(edge, 2)).toBe(
      "linear-gradient(to top, #111111 0px 4px, #222222 4px 6px, #333333 6px 8px, #444444 8px, #555555 20px)",
    );
  });
});

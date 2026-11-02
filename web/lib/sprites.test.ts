import { describe, expect, it } from "vitest";
import { pixelRuns } from "./sprites";

describe("pixelRuns", () => {
  it("merges same-colour pixels per row and skips transparent ones", () => {
    const runs = pixelRuns({ grid: ["aa.b", "...."], palette: { a: "#111111", b: "#222222" } });
    expect(runs).toEqual([
      { x: 0, y: 0, width: 2, fill: "#111111" },
      { x: 3, y: 0, width: 1, fill: "#222222" },
    ]);
  });

  it("covers exactly the opaque pixels", () => {
    const sprite = { grid: ["k.kk", ".kk.", "kkkk"], palette: { k: "currentColor" } };
    expect(pixelRuns(sprite).reduce((n, r) => n + r.width, 0)).toBe(9);
  });
});

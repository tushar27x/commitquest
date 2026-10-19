// Tab bar icons: 9×9 one-colour grids in `currentColor`, so a tab's text colour paints them
// (gold when active, muted otherwise). Drawn in web/ for now; integration moves them into
// docs/design/sprites.json later.

import type { Tab } from "./nav";
import type { Sprite } from "./sprites";

const ICON_PALETTE = { c: "currentColor" } as const;

const GRIDS: Record<Tab["href"], readonly string[]> = {
  // A house with a door.
  "/home": [
    "....c....",
    "...ccc...",
    "..ccccc..",
    ".ccccccc.",
    "ccccccccc",
    ".ccccccc.",
    ".ccc.ccc.",
    ".ccc.ccc.",
    ".ccc.ccc.",
  ],
  // An open book: two ruled pages either side of the spine.
  "/journal": [
    ".........",
    "cccc.cccc",
    "c..c.c..c",
    "c.cc.cc.c",
    "c..c.c..c",
    "c.cc.cc.c",
    "c..c.c..c",
    "ccccccccc",
    "....c....",
  ],
  // A knight's helmet with an eye slit.
  "/hero": [
    "...ccc...",
    ".ccccccc.",
    "ccccccccc",
    "ccccccccc",
    "cc.....cc",
    "ccccccccc",
    "cccc.cccc",
    "cccc.cccc",
    ".ccc.ccc.",
  ],
  // Crossed swords.
  "/arena": [
    "c.......c",
    "cc.....cc",
    ".cc...cc.",
    "..cc.cc..",
    "...ccc...",
    "..cc.cc..",
    "ccc...ccc",
    ".c.....c.",
    "c.......c",
  ],
  // A gear.
  "/settings": [
    "....c....",
    ".c.ccc.c.",
    "..ccccc..",
    ".cc...cc.",
    "ccc...ccc",
    ".cc...cc.",
    "..ccccc..",
    ".c.ccc.c.",
    "....c....",
  ],
};

export function tabIcon(href: Tab["href"]): Sprite {
  return { grid: GRIDS[href], palette: ICON_PALETTE };
}

# Design: Pixel Realm mockups

The approved UI. Build `web/` to match these images. The rules behind them are in `docs/v2-hero-plan.md` §4–5.

**Live canvases** (claude.ai, private to the owner):
- Character Design: https://claude.ai/artifact/1qrPrfpjU94UCYh2c2C2Nb
- Home & Journal: https://claude.ai/artifact/3vjimL2VeHgExJnuB7BNQt

## Images (`png/`)

| File | Screen | Size |
|---|---|---|
| `title-phone.png` | Title screen `/` | 390×844 |
| `home-phone.png` | Home (pet), phone. Fixture: LV 7, 2 hearts, HUNGRY | 390×844 |
| `home-desktop.png` | Home, desktop (top nav, scene left, cards right) | 1280 wide |
| `home-backdrop-tiers.png` | The 4 level-tier backdrops: Campfire / Village / Keep / Citadel | 1440×460 |
| `journal-phone.png` | Journal (stats, 28-day calendar, top repos, year in review) | 390×844 |
| `settings-phone.png` | Settings (rest-day picker, timezone, toggles, log out) | 390×844 |
| `character-sheet-phone.png` | Hero tab, phone | 390×844 |
| `character-sheet-desktop.png` | Hero tab, desktop | 1280 wide |
| `change-class-phone.png` | Change class (qualified vs NEEDS 40, confirm) | 390×844 |
| `character-roster.png` | All 8 class sprites with habit text | 1440×1000 |
| `moods-monk.png` | The 7 moods as shared overlays | 1440×520 |
| `palette-and-type.png` | Palette swatches, type scale, button states, parchment box | 640×940 |
| `system-states.png` | Saving / Resting / New game / Reconnect / Session ended / Road blocked | 1440×600 |

## Data

- `tokens.json`: colours, class accents, fonts, pixel rules, tier ranges, mood rules. Map these into Tailwind 4 `@theme`.
- `sprites.json`: all 8 class grids (16×16) with palettes, plus heart, tree, sparkle and sweat icons. **Use this file directly** in `web/lib/sprites.ts` (import or copy); don't retype grids.

## Board sources (`source/*.dc.html`)

The exact markup and inline CSS of each board, from the design canvas. They use a canvas-only template format (`<x-dc>`, `<sc-for>`, `{{holes}}`, a `DCLogic` class), so **don't run them**. Read them as a reference for exact spacing, sizes, shadows and colours when the PNG isn't precise enough. Every number in them is fixture data.

Notes:
- Numbers and repo names in the images are fixture data, matching `docs/api/fixtures/`.
- AI text in the images is sample copy. In the app it comes from the API and always sits on parchment.
- Interactive boards (change class, settings) are shown in their default state.

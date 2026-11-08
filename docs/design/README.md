# Design: Pixel Realm mockups

The approved UI. Build `web/` to match it.

**Live canvas** (claude.ai, owner only; since 2026-11-02):
- **Hero Design (hi-res):** https://claude.ai/artifact/WpSMuuZevpRYoK9M99honc. It replaces the two canvases below for the art and the boards.
- Old canvases, kept for history: Character Design https://claude.ai/artifact/1qrPrfpjU94UCYh2c2C2Nb · Home & Journal https://claude.ai/artifact/3vjimL2VeHgExJnuB7BNQt

## Backdrops redrawn (2026-11-03, U8)

The developer replaced every scene: the 4 tiers at phone, desktop and card size, and the title. Same file names and sizes, so they drop in. The style moved from wide landscapes to closer, warmer night scenes (e.g. the village is now a lamp-lit street). The campfire, its glow and the hero sit at new positions on the `Tiers` and `TierHome` boards: take the overlay positions from those sources, not from the old ones. Nothing else changed: heroes, moods, hearts, icons, portraits and the other boards are as in U7.

## What changed in the hi-res design (U7)

- **Heroes are pixel art in PNG sprite sheets**, not 16×16 grids: 96×128 per frame (1 art pixel = 1 CSS pixel at ×1), a 60-frame idle loop that repeats without a seam, drawn at ×1, ×2 (192×256) or ×3 (288×384). Shaded ramps lit from the top left, a cool rim light on the right, dark tinted outlines.
- **All 8 classes have all 7 moods** as their own animations. The old shared overlays (sweat, Zzz, sparkles) are no longer drawn in CSS. Level up adds light rays behind the hop. Fainted lays the sprite down with stars circling.
- **Backdrops are PNG scenes** per tier, one for phone and one for desktop, plus a title scene. Only the overlays move: campfire flames, twinkling stars.
- **Hearts, nav icons, class portraits and the campfire** are small PNG sheets too.
- Layout, copy, palette and the fixture story are the same as before. Core tokens did not change. The new colours in the boards are mostly shading inside the art.

## Art (`art/`)

All PNGs, pixel art: render with `image-rendering: pixelated` at integer scales only. Animate with CSS `steps()` on `background-position`, and stop every animation under `prefers-reduced-motion: reduce`.

| Path | Size | What | How the boards use it |
|---|---|---|---|
| `heroes/idle/<class>.png` (8) | 5760×128 | Idle (OKAY mood): 60 frames of 96×128 in one row | `steps(60)` over **6.2832 s**, infinite. ×2: `background-size: 11520px 256px` |
| `heroes/moods/<class>.png` (7, every class except Monk) | 5760×768 | The 6 other moods, one row each, 60 frames per row. Rows top to bottom: **HAPPY, WEAK, HUNGRY, SLEEPING, LEVEL UP, FAINTED** | Same timing. Row *n* = `background-position-y: -(n × 128)px` at ×1 |
| `heroes/moods/monk-<mood>.png` (7) | 5760×128 | Monk's moods as separate sheets: `happy`, `weak`, `hungry`, `sleeping`, `levelup`, `fainted`, and `hungry-bubble` | Home uses `hungry` (no speech bubble) and draws the bubble in HTML. `hungry-bubble` has it drawn in; only the Moods board shows it |
| `scenes/<tier>-phone.png` | 390×292 | Home backdrop, phone, ×1 | `campfire` LV 1–4, `village` 5–9, `keep` 10–14, `citadel` 15+ (`tokens.json` `tiers`) |
| `scenes/<tier>-desktop.png` | 380×260 | Home backdrop, desktop | Drawn at ×2: `background-size: 760px 520px` |
| `scenes/<tier>-card.png` | 312×250 | The small tier cards on the "Level tiers" board | Optional in the app |
| `scenes/title.png` | 390×600 | Title screen scene | |
| `landing/*.png` (10) | 960 wide | Landing page scenery (`/`): `sky`, `far`, `mid`, `mist`, `near` (960×640 / 480), `clouds-high`, `clouds-low`, `castle`, `castle-lights` (316×416), `sun` (640×560) | Tiled and drawn at ×1 on phones, ×1.5 from 768 px (`web/app/landing.css`) |
| `scenes/live/*.png` (55) | various | The animated Home backdrops (since 2026-11-08): the layers of the four tier scenes (Campfire boreal forest, Village river valley, Keep highland moor, Citadel alpine summit), named by the first 8 hex digits of their canvas id | Drawn on a 960×540 stage (`web/components/home/scene-layers.ts`, `SceneStage.tsx`, `app/scenes.css`), scaled to the screen height; the phone and desktop `scenes/<tier>-*.png` are no longer used by Home |
| `ui/hearts.png` | 312×22 | 13 frames of 24×22. Frames 0–11: the full heart's beat loop. Frame 12 (`-288px`): the empty heart | Full: `steps(12)` over 1.2 s, each heart offset by −0.3 s |
| `ui/nav-icons.png` | 120×48 | 5 tab icons of 24×24 (HOME, JOURNAL, HERO, ARENA, SETTINGS). Row 0 idle, row 1 active | `x = -(i × 24)px`, `y = 0` or `-24px` |
| `ui/portraits.png` | 320×48 | 8 class portraits of 40×48, in class order (Paladin, Barbarian, Wizard, Artificer, Cleric, Bard, Monk, Rogue) | Change-class rows; `grayscale(1) brightness(0.7)` when not qualified |
| `ui/campfire.png` | 256×48 | 8 frames of 32×48 | `steps(8)` over 0.8 s |

`sprites.json` (the 16×16 grids) is superseded by `art/`. It stays until `web/` no longer imports it; U7 can delete `web/lib/design/sprites.json` and its sync.

## Board sources (`source/`)

`source/*.dc.html` is the exact markup and inline CSS of each board in the hi-res canvas, and `source/canvas.json` its layout and sticky notes. The format is canvas-only (`<x-dc>`, `<sc-for>`, `{{holes}}`, a `DCLogic` class), so **don't run them**: read them for exact sizes, positions, shadows and colours. They reference the art as `/_blob/<id>`; the table maps every id to a file here.

| Board | Screen |
|---|---|
| `Main` | Class roster: the 8 heroes at ×2 with habit text |
| `Tokens` | Palette, type scale, buttons, parchment |
| `SheetMobile`, `SheetDesktop` | Hero tab (character sheet) |
| `Moods` | The 7 moods, Monk |
| `MoodMatrix` | 8 classes × 7 moods |
| `ClassSwitch` | Change class (interactive) |
| `HomeMobile`, `HomeDesktop` | Home (pet). Fixture: LV 7, 2 hearts, HUNGRY |
| `JournalMobile`, `SettingsMobile`, `Title` | Journal, Settings, Title `/` |
| `Tiers`, `TierHome` | Backdrop per level tier; Home on every tier at both sizes |
| `States` | Saving / Resting / New game / Reconnect / Session ended / Road blocked |

| `/_blob/` id | File |
|---|---|
| `f18954a9…` `8ed10d55…` `0e4e4ce8…` `773d1670…` `5da76443…` `81732435…` `abcc5510…` `aa0ac593…` | `heroes/idle/` paladin, barbarian, wizard, artificer, cleric, bard, monk, rogue (CSS `.k-<class>`) |
| `9e835526…` `66fd421e…` `0a005124…` `765fe7ca…` `4b8af6fc…` `9b74601d…` `4083877a…` | `heroes/moods/` paladin, barbarian, wizard, artificer, cleric, bard, rogue (`.a-<class>`, rows `.r0`–`.r5`) |
| `0a47e55b…` `bf494fff…` `0cb33118…` `72913c88…` `4f5904c1…` `f95aab39…` `c4ebdb61…` | `heroes/moods/monk-` happy, weak, hungry, hungry-bubble, sleeping, levelup, fainted (`.k-monk-<mood>`; `-hungry-nb` = `monk-hungry`) |
| `00e9012f…` `86bfea0f…` `1e020d61…` `14f1bbf2…` | `scenes/` campfire, village, keep, citadel `-phone` (`.sd-<tier>-p`, `.sc-vphone`) |
| `f5a3c153…` `0dc9803b…` `7237a28b…` `21498b85…` | `scenes/` campfire, village, keep, citadel `-desktop` (`.sd-<tier>-d`, `.sc-vdesk`) |
| `1f82edac…` `71946e83…` `c4c1d5ad…` `7641cacf…` | `scenes/` campfire, village, keep, citadel `-card` (`.sc-camp`, `.sc-vill`, `.sc-keep`, `.sc-cita`) |
| `292362e5…` | `scenes/title.png` (`.sc-title`) |
| `1206abce…` `71aa271d…` `43b2b41e…` `93bf0f74…` | `ui/` hearts (`.ht`), nav-icons (`.ic`), portraits (`.pt`), campfire (`.fire`) |

## Images (`png/`) and data

- `png/` holds renders of the **old** canvases. Layout, spacing and copy still match; the art in them is out of date. Use the board sources and `art/` for the look. New renders: export them from the canvas (Share › Export) when needed.
- `tokens.json`: colours, class accents, fonts, pixel rules, tier ranges, mood rules. `web/scripts/sync-design.mjs` turns it into `web/app/theme.css`.

Notes:
- Numbers and repo names in the boards are fixture data, matching `docs/api/fixtures/`.
- AI text in the boards is sample copy. In the app it comes from the API and always sits on parchment.
- Interactive boards (change class, settings) are shown in their default state.

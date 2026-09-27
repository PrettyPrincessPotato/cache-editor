# Font cache format — research notes & plan

Captured while reverse-engineering how JS5 fonts work, so we can pick this back up later.
Two projects are the source of truth:
- **`/home/princess/Projects/Void/`** — the server (Kotlin). Decodes **metrics** only.
- **`/home/princess/Projects/void-client/`** — the deobfuscated client (Kotlin Multiplatform). Decodes **metrics + glyph sprites**.

---

## TL;DR

A "font" in the cache is **three coordinated pieces, all keyed by one id**:

| Piece | Archive | What it is | Where we got the format |
|---|---|---|---|
| Glyph sprite | `JAGEX_FONTS` (34, or 32 non-antialiased) | per-glyph bitmaps — what the client *draws* | client `SpriteImage.method1517` |
| Metrics | `FONT_METRICS` (13) | per-glyph widths, kerning, padding, spacing — what the client *measures* | Void `FontDecoder` + client `FontDefinition` |
| Registry | (server `fonts.toml`) | `name = id` lookup | Void `FontDefinitions.load` |

- **Addressing:** `group(id) = id`, `file(id) = 0` (one file per group). *Not* the stub's `id ushr 7` / `id and 0x7f`.
- **256 glyphs** per font file; **sprite index = char code 0..255** (Latin-1 / Cp1252).
- The client composites all 256 glyphs into a single **16×16 cell texture** at runtime; glyph `c` sits at cell `(c % 16, c / 16)`.
- **Fonts are NOT opcode streams** (unlike NPC). Both archives are fixed-layout binary tables. So "do the same as NPC" holds for the *editor architecture* but **not** the *byte layer* — the decoder/encoder must be a fixed-table reader/writer, not an opcode dispatcher.

---

## FONT_METRICS (archive 13) — byte layout

Confirmed by **both** Void's `FontDecoder` and the client's deobfuscated `FontDefinition` `init`, and verified against our **263-byte** dump (every font file is 263 bytes → `hasKerningTable = false`).

```
offset 0   1 byte   marker (must be 0, else reader aborts)
offset 1   1 byte   hasKerningTable (bool)
offset 2   256      glyphWidths[glyph]  (u8, advance width per glyph)
if hasKerningTable:
          256      rightOffsets[glyph]  (u8)
          256      leftOffsets[glyph]   (u8)
          Σright   rightGlyphData       (packed, per-glyph, length = rightOffsets[g])
          Σleft    leftGlyphData        (packed, per-glyph, length = leftOffsets[g])
          (kerning is COMPUTED from these; verticalSpacing = leftOffsets[32] + rightOffsets[32])
else:
          1 byte   verticalSpacing
then:
          2 bytes  (skipped / padding)
          1 byte   topPadding
          1 byte   bottomPadding
```

No-kerning case: `1 + 1 + 256 + 1 + 2 + 1 + 1 = 263` bytes. ✓

Model fields (`Void FontDefinition`): `id`, `glyphWidths: ByteArray(256)`, `kerningAdjustments: Array<ByteArray>?` (256×256, computed), `verticalSpacing`, `topPadding`, `bottomPadding`, `stringId`, `params`.

## JAGEX_FONTS (archive 34 / 32) — byte layout (the glyph sprite)

From client **`SpriteImage.method1517`** — this is the *generic* JS5 sprite-file decoder (fonts, cursors, icons, hitsplats all share it). All multi-byte values **big-endian**. Layout after decompression, from offset 0:

```
[0 ..]    PIXEL DATA, one block per glyph, index order 0..count-1:
           1 byte  flag:
             bit0 (0x1) = 0 → palette indices row-major (w per row)
                          1 → palette indices column-major (h per col, top→bottom)
             bit1 (0x2) = 1 → an extra alpha byte per pixel follows the indices
           w*h bytes  palette indices (1 byte each; index 0 = transparent)
           [if bit1] w*h bytes alpha (0xFF = opaque)
      then  PALETTE: (colors - 1) × 3 bytes RGB   (slot 0 is implicit = transparent, NOT stored)
      then  COUNT × 2 bytes  per-glyph X offset
      then  COUNT × 2 bytes  per-glyph Y offset
      then  COUNT × 2 bytes  per-glyph WIDTH
      then  COUNT × 2 bytes  per-glyph HEIGHT
      then  2 bytes  cell total width   (must cover xOff + width for every glyph)
      then  2 bytes  cell total height  (must cover yOff + height for every glyph)
      then  1 byte   (colors - 1)
last 2    COUNT (unsigned short; = 256 for a font)
```

Raw archive framing (before decompression, `CutsceneSequenceData.method3158`): `[1 byte flag][4 bytes decompressed size][payload]`; flag `0`=raw, `1`=zlib, else gzip. (Our `CacheLibrary.data(...)` already returns the **decompressed** bytes, so we work from the layout above.)

**Monochrome font (what the client actually uses — `bool=true` everywhere):** palette = [transparent, white]; flag byte `0` (row-major, no alpha); per glyph, `w*h` palette-index bytes (`0` = clear, `1` = ink).

### Client sprite-sheet build (`GlFontTextured`)
```
i  = max over all 256 glyphs of (width, height)     // cell size in px
texture = 16×16 grid of i×i cells  (16*i × 16*i px)
glyph c → cell (c % 16, c / 16), offset within cell by (xOff, yOff)
ink mask = (palette index != 0)
```
e.g. p11 ≈ 11×11 cells → ~176×176 px texture.

## How the client loads & references fonts (load chain)

- `ClientLoadStateMachine.kt`:
  - line 76: glyph archive = **34** (antialiased) or **32** (non-antialiased), chosen by a setting flag.
  - line 78: metrics archive = **13**.
  - lines 82–85: sanity check — file counts of archives 33 + (34|32) + 13 must total **400**.
- `DualMaterialContainer.kt` (method1001, lines 70–72): resolves the built-in font **file ids by name** from the glyph archive's index:
  ```
  p11_full, p12_full, b12_full   (via js5Archive.method417("<name>", i))
  ```
- `ScrollbarComponent.method184` → the id list `intArrayOf(p11, p12, b12)`.
- `VarpStore.method1311`: for each font id, builds `FontDefinition` (archive 13) **+** `SpriteImage` (archive 34), same numeric id, → `GlFontTextured`.
- The same sprite file format also appears in the **SPRITES archive (8)** for loading-screen fonts.

## Registry (server)

`Void/data/entity/player/modal/fonts.toml` — maps a **role name → font id** (server-side lookup; the client resolves the same names by file name in the archive index):
```
friendslist_font = 305      tutorial_font = 307
welcome_font_small = 468    welcome_font_large = 473
q8_full = 497               tutorial_font_big = 584
menu_font_small = 591       quill_oblique_large = 645
quill_caps_large = 646      lunar_alphabet = 647
lunar_alphabet_lrg = 648    barbassault_font = 764
tzhaar_numbers = 776        surok_font = 819
verdana_15pt_regular = 3795 verdana_13pt_regular = 4040
p11_full = 494              p12_full = 495
b12_full = 496              verdana_11pt_regular = 3793
```
Matches the dump exactly (metrics groups 305/307/468/473; glyph groups 494/495/496 = p11/p12/b12).

---

## How to author / import a NEW font (the "important step")

1. **Pick a free font id** — a number not already used in the registry / archives.
2. **Author the glyph sprite** → `JAGEX_FONTS` file at `(group=id, file=0)`: 256 glyphs in char-code order, monochrome (flag `0`, palette [transparent, white], `w*h` palette-index bytes each), then palette, then the per-glyph x/y/w/h shorts, the 5-byte tail, and the 2-byte count. Cell dims must cover every glyph's `offset+size`.
3. **Author the metrics** → `FONT_METRICS` file at `(group=id, file=0)`: `glyphWidths` (must match the sprite glyph widths), `topPadding`, `bottomPadding`, `verticalSpacing` (no kerning → `hasKerningTable=false`, 263 bytes).
4. **Register + reference** — the client looks the font up **by name** from the glyph archive index, so the new file needs a name in the index (and, server-side, a `name = id` line). Then point that name at wherever the client should render with it.

> A new font is only "used" by the client once **both** archives have the matching id **and** the name resolves. Editing one without the other gives a broken/blank font.

---

## Scaffold corrections needed in `cache-editor`

The current font scaffold (mirrors NPC) is structurally right but the **byte model is wrong** for fonts:

- [`src/main/kotlin/cache/decode/FontJs5Archive.kt`](src/main/kotlin/cache/decode/FontJs5Archive.kt)
  - `archive()` → `Js5.FONTMETRICS` (13) for the *field editor* (the sprite is a separate image concern).
  - `group(id) = id`, `file(id) = 0`.
  - Replace the opcode-dispatch `readOpcode` with a **single fixed-layout read** (the metrics layout above).
- [`src/main/kotlin/cache/types/FontType.kt`](src/main/kotlin/cache/types/FontType.kt)
  - Replace stub `name` / `placeholder` / `placeholderFlag` with: `glyphWidths: ByteArray`, `hasKerningTable: Boolean`, `verticalSpacing`, `topPadding`, `bottomPadding` (+ kerning if present).
- [`src/main/kotlin/cache/encode/FontTypeEncoder.kt`](src/main/kotlin/cache/encode/FontTypeEncoder.kt) — exact inverse of the metrics decoder (no default-skip; fixed layout).
- [`src/test/kotlin/cache/FontTypeRoundTripTest.kt`](src/test/kotlin/cache/FontTypeRoundTripTest.kt) — replace the stub test with a **full-cache byte-identity** test (re-encode every font, assert `decode(encode(x)) == x`), per `NpcTypeRoundTripTest`.
- `editor/font/*` (Repository/Fields/Form/Tab/TabContent) — keep the 7-layer architecture, but the editable *fields* become the metric fields (a 256-wide `glyphWidths` may need a dedicated UI, not a plain text field).

Separate (larger) piece: a **sprite authoring/writing** tool for `JAGEX_FONTS` (render a TTF → 256 monochrome glyphs → the sprite layout above). That's how you'd actually *make* a brand-new font's pixels.

---

## Open questions / gaps

1. **Client-side name→id:** exactly how the client resolves a font name to a file id (archive-index file names vs. a config). `DualMaterialContainer.method1001` uses `js5Archive.method417("p11_full", i)` — confirm whether the *index* stores file names, and how a new font's name gets into it.
2. **Antialiased vs not:** archive 34 vs 32 — which does this client build use (setting flag in `ClientLoadStateMachine`), and do we need both for a new font?
3. **The 400-file sanity check** — does adding a font require keeping the 13+34(+33) totals at 400, or is that just a load-time invariant?
4. **Metrics `stringId` / params** — are they used client-side or server-only?
5. **Kerning** — all fonts in the dump are `hasKerningTable=false`; do any real fonts use the kerning path (needed to implement the encoder's kerning branch)?

## Immediate next steps (in suggested order)

1. Rebuild the **`FONT_METRICS`** editor around the confirmed layout: real `FontType` + fixed-layout `FontJs5Archive` + inverse `FontTypeEncoder`, and a **byte-identity round-trip test against the real cache**. This is the "do the same as NPC, done correctly" milestone.
2. Confirm the **client name→id resolution** (open question 1) so "import a new font" is airtight.
3. Build the **`JAGEX_FONTS` sprite writer** (render TTF → monochrome 256-glyph sprite per the layout above), and a viewer that composites the 16×16 texture so you can *see* your result.
4. End-to-end: create a test font id, write both files + registry entry, and confirm the client renders it.

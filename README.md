# SINA TGS Studio

Offline Android editor for Telegram TGS (Lottie) stickers. Kotlin + Jetpack Compose. No server, no internet.

## Modules
| module | what it does | depends on |
|---|---|---|
| `document` | Lossless JSON tree + editable Lottie model (layers, groups, keyframes). Pure Kotlin. | – |
| `parser` | `TgsCodec`: gzip ⇄ Lottie JSON, size-bounded. | document |
| `renderer` | Frame evaluation (keyframes, easing, motion paths) → vector scene (shapes, gradients, trim, masks, mattes, precomps). Pure Kotlin. | document |
| `editor` | `EditorSession`: selection, hit-testing, transform/style/path editing, keyframes, layers & groups, undo/redo, SVG import, centerline text. Pure Kotlin. | renderer |
| `export` | Telegram-limit validator, TGS / Lottie JSON / SVG exporters. Pure Kotlin. | parser, editor |
| `app` | Compose UI: gallery, canvas, layers, properties, timeline, add panel. | all |

The raw JSON tree is the source of truth; model classes are thin views over it, so anything the editor
doesn't understand is written back untouched. Undo/redo restores whole-document snapshots.

## Build
Push to GitHub: the workflow runs every module's unit tests and builds the debug APK (artifact + Release).
Locally: `gradle :document:test :parser:test :renderer:test :editor:test :export:test :app:assembleDebug` (Gradle 8.9, JDK 17).

## Telegram TGS compatibility
- Keyframes: every keyframe except the last must carry easing handles (`i`/`o`) or be a hold (`h:1`); the last must carry none.
  rlottie drops violating keyframes (and crashed on path keyframes without them in our tests). `PropWrite.normalize` enforces this on
  every edit, and the exporter repairs a copy of the document (`TgsPrepare`) before writing.
- Star/polygon tools create plain `sh` paths (Telegram lists star shapes as unsupported).
- `Validator` checks Telegram's published rules (512x512, <=3 s, 60 fps, <=64 KB, prohibited features) and the keyframe structure.
- `tools/rlottie_check.py` renders the fixtures written by `RlottieFixtureTest` with rlottie and compares them with our renderer.
  This is strong evidence, not a guarantee that Telegram's own apps and servers accept a file.

## Editing features
- **Parent / child**: real Lottie layer parenting (`ParentOps`). Linking keeps the child where it is on screen (when its transform is static), refuses
  loops, shows the hierarchy in the Layers panel, and re-parents children when their parent is deleted. "Create parent (Null)" builds a pivot layer.
- **Trim Paths**: Start/End/Offset with keyframes; quick Draw on / Draw off / Erase from start (`TrimOps`).
- **Pen**: freehand strokes become editable path layers (colour, width, opacity), optional write-on animation, eraser for pen strokes.
- **Effects** (`EffectEngine`): fade, zoom, shake, bounce, pulse, rotation, scale, slide, opacity, float, draw on/off, glow. Transform effects animate a
  Null parent that rests at the identity, so they stack and can be removed; opacity effects compose with existing opacity animation. Blur is refused
  because TGS forbids layer effects. Custom presets are saved on the device.

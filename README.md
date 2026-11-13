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

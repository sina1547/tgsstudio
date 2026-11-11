# SINA TGS Studio

Offline Android editor for Telegram TGS (Lottie) stickers. Kotlin + Jetpack Compose.

## Modules
- `document` — pure Kotlin. Lossless JSON tree + editable Lottie model (layers, shape groups, keyframes). No Android or UI dependency.
- `parser` — pure Kotlin. `TgsCodec`: gzip ⇄ Lottie JSON, size-bounded.
- `app` — Compose UI (stage 1: open a TGS and inspect its layer tree).

## Design rule
The raw JSON tree is the source of truth; model classes are thin views over it. Anything the editor
doesn't understand stays in the tree and is written back untouched.

## Stages
1. [x] Project, architecture, TGS reader, document model (this commit)
2. [ ] Renderer
3. [ ] Layers panel and selection
4. [ ] Transform / fill / stroke / path editing
5. [ ] New shapes
6. [ ] Timeline and keyframes
7. [ ] Undo / redo
8. [ ] TGS writer validation and export
9. [ ] Full tests and release APK

## Build
Push to GitHub: the workflow runs the unit tests and builds the debug APK (artifact + Release).
Locally: `gradle :document:test :parser:test :app:assembleDebug` (Gradle 8.9, JDK 17).

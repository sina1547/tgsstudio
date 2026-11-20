# TGS Studio — Pro Editor Development Roadmap

This roadmap is the implementation contract for evolving TGS Studio into a general-purpose Android motion-graphics editor while retaining reliable Telegram TGS export. Features are not marked complete merely because a model or UI control exists: each needs an end-to-end edit/render/export path and tests.

## Existing foundation (verified in the codebase)

- [x] Six-module Kotlin architecture: document, parser, renderer, editor, export, Android app.
- [x] Raw Lottie JSON tree remains the source of truth; unknown JSON nodes survive round-trips.
- [x] Shape/layer selection, reorder, duplicate, group/ungroup, visibility and lock.
- [x] Parent/child hierarchy, Null parent creation, detach and keep-position reparenting.
- [x] Shape/path editing, node operations, SVG import, rectangle/ellipse/star/polygon/freehand creation.
- [x] Transform and appearance editing with undo/redo.
- [x] Keyframe creation, movement, deletion, value editing, copy/paste, interpolation and Bezier easing.
- [x] Visual Bezier Graph Editor.
- [x] Timeline zoom, playhead scrubbing and keyframe tracks for the selected target.
- [x] Preset-based editable animation effects, Trim Paths, Glow, and effect Null layers.
- [x] Renderer support for solid/gradient paint, masks and alpha mattes; support is not yet equivalent to a full Lottie/After Effects compatibility matrix.
- [x] Round Corners modifier and geometry tests.
- [x] Persistent TTF/OTF import and vectorized text creation (centerline and filled outline modes).
- [x] TGS, Lottie JSON and SVG-frame export.
- [x] Timeline controls for frame stepping, direct playhead entry, work-area In/Out and frame-rate editing.

## Development milestones

### M1 — Animation workspace and timeline (active)
- [ ] Multi-layer timeline with clip bars, layer selection and per-layer in/out editing.
- [ ] Expand/collapse transform and shape-property tracks per layer.
- [ ] Multi-keyframe selection, range selection, nudge, duplicate and batch delete.
- [ ] Snap-to-frame, playhead/keyframe navigation, work-area loop and markers.
- [ ] Graph Editor with value curves, channel selection and tangent modes.
- [ ] Undo-safe drag gestures and timeline performance tests.

### M2 — Layer and composition system
- [ ] Full Pre-compose workflow with editable nested compositions.
- [ ] Null, parent, child and constraint controls exposed consistently in the UI.
- [ ] Track matte modes and blend modes with renderer/export conformance tests.
- [ ] Layer in/out, stretch, start-time and time-remap controls.
- [ ] Multi-select property editing and alignment/distribution tools.

### M3 — Shape and typography
- [ ] Full Bezier pen workflow: handles, tangent conversion, path join/split and path boolean operations.
- [ ] Shape repeater, merge paths, rounded corners and gradient fill/stroke editing.
- [ ] Linear/radial gradient editor with multi-stop colors, alpha stops and animated properties.
- [ ] Font library, family/style selection, tracking, baseline, paragraph alignment and per-project font references.
- [ ] Live text model for editable project text; vector outlines remain the reliable TGS export fallback.
- [ ] Eyedropper sampling from the rendered composition.

### M4 — Masks, effects and renderer compatibility
- [ ] UI for creating/editing/animating masks (add, subtract, intersect, difference, inverted).
- [ ] Feather, expansion and mask opacity where format/runtime support permits.
- [ ] Expand Lottie compatibility tests for image assets, text, mattes, blend modes, repeater, merge paths, time remap and modifiers.
- [ ] Preserve unknown nodes and unsupported effects without destructive rewrites.
- [ ] Effect stack with animated parameters, reorder, enable/disable and per-effect reset.

### M5 — Import, preview and media pipeline
- [ ] Multi-file import for SVG, Lottie JSON, TGS, PNG, JPG, WebP, GIF and WebM.
- [ ] Image asset management, relinking, project-relative asset packaging and missing-asset diagnostics.
- [ ] Real-time preview optimization, caching, playback frame pacing and memory limits.
- [ ] Deterministic frame renderer with cancellation, progress, bounded memory and render queue.

### M6 — Export and release quality
- [ ] GIF export with transparency and palette/dithering controls.
- [ ] MP4 export using device-validated hardware/software encoders.
- [ ] WebM VP9 alpha export only when the device codec path is verified to preserve alpha.
- [ ] TGS validation, size budget, frame-rate and Telegram conformance checks.
- [ ] Instrumented device tests for import → edit → save/reopen → playback → export.
- [ ] Stable signing/update path, versioned APK releases and regression suite.

## Current increment

The current branch adds timeline frame stepping, direct frame entry, keyframe insertion at the playhead, editable composition work area and frame-rate controls. Work-area changes retain keyframes outside the active range rather than deleting animation data.

## Acceptance gates

1. Build and all JVM tests pass.
2. New editor operation has undo/redo coverage.
3. Imported unknown Lottie JSON nodes are preserved.
4. UI control is connected to the actual model and renderer.
5. Export output is validated; codec/alpha claims require device verification.
6. A release APK is published only from a successful build.

# Pro Editor Roadmap

This roadmap tracks implementation status in the existing six-module architecture.

## Implemented in feature/pro-editor-suite
- [x] Lottie Round Corners modifier in the pure Kotlin renderer, operating on cubic contours.
- [x] Unit coverage for rounded geometry and scene integration.
- [x] Interactive cubic-Bezier graph editor connected to selected keyframe easing.
- [x] Persisted Dark, Light, Neon and Custom themes with a user-defined HEX accent.
- [x] Import a TTF/OTF font into app-private storage and use it for vectorized text creation.

## Remaining engineering work
- [ ] Complete the Lottie renderer compatibility matrix: image assets, text layers, masks, mattes, blend modes, repeater, merge paths, time remap, and additional modifiers. Preserve unknown JSON nodes.
- [ ] Add a true outline text pipeline with font shaping and glyph outlines; retain the current centerline option as a separate mode.
- [ ] Add a font library UI with multiple installed fonts, family/style selection, weight, tracking, line spacing, and per-project font references.
- [ ] Add an eyedropper that samples the rendered composition and returns the sampled RGBA color to the active property.
- [ ] Add an editable gradient model and UI for linear/radial gradients, stop positions, stop colors, opacity, and animated gradient properties.
- [ ] Add an animation export pipeline with deterministic frame rendering, cancellation, progress, memory bounds, and output validation.
- [ ] Implement GIF export (including transparency) and device-validated MP4/WebM encoders. WebM alpha must only be advertised on devices/codecs that actually encode alpha.
- [ ] Add UI/instrumentation tests and device fixtures for import, edit, save/reopen, playback, and export.

## Acceptance requirements
- No feature is considered complete merely because a data class or toolbar control exists.
- Each feature must be reachable from the UI, preserve unknown Lottie JSON, have unit tests where applicable, and pass the CI build.
- Video codecs and alpha-channel behavior require runtime capability checks and real-device verification.

# Professional development roadmap

This project is being advanced incrementally without replacing its working document, parser, renderer, editor, or TGS export modules.

## Foundation
- [x] Immersive editor workspace
- [x] Debounced autosave plus lifecycle save fallback
- [ ] Crash-recovery journal and project snapshots
- [ ] Stable release signing configuration (requires owner-controlled signing secrets)

## Timeline and animation
- [ ] Multi-layer track overview with independent in/out ranges
- [ ] Graph editor with editable Bezier velocity curves
- [ ] Motion paths and spatial interpolation controls
- [ ] Time remapping and camera layers
- [ ] Audio tracks, waveform and sync

## Design and assets
- [ ] Raster image assets (PNG/JPEG/WebP) with explicit asset packaging strategy
- [ ] Live text layer model with font import and shaping
- [ ] Gradient, eyedropper, masks and clipping
- [ ] Boolean path operations and expanded vector editing

## Effects and rendering
- [ ] Parameterized effect stack and reusable presets
- [ ] Offscreen render pipeline and render queue
- [ ] PNG sequence, GIF, animated WebP, alpha WebM and MP4
- [ ] Export profiles for dimensions, frame rate, quality and alpha

## Quality
- [ ] UI automation and device-level regression tests
- [ ] Large-document performance and memory benchmarks
- [ ] Recovery, import/export round-trip and malformed-file tests
- [ ] Signed release APK and upgrade-path verification

## Engineering constraints
- Preserve TGS compatibility and the existing validated export path.
- Do not silently flatten editable content or claim unsupported export formats.
- Keep unsupported Telegram features visible through the existing validator.
- Add each feature with tests and CI validation before marking it complete.

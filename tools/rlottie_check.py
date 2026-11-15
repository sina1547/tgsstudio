#!/usr/bin/env python3
"""Renders the TGS fixtures written by RlottieFixtureTest with rlottie and verifies them.

Two kinds of check per fixture (see `<name>.expect.json`):
  * analytic: centroid / bounding box / opacity / ink area at given frames
  * cross-render: IoU between rlottie's frame and this project's own renderer (exported as SVG, drawn with cairosvg)

rlottie is the renderer family Telegram's players are based on. Passing here is strong evidence, not a guarantee
that Telegram's own apps or servers accept a file.

usage: python tools/rlottie_check.py <fixtures-dir>
requires: pip install rlottie-python numpy pillow cairosvg
"""
import glob, io, json, math, os, sys
import numpy as np
from rlottie_python import LottieAnimation

SIZE = 512


def render(anim, frame):
    im = anim.render_pillow_frame(frame_num=frame, width=SIZE, height=SIZE).convert("RGBA")
    return np.array(im)[:, :, 3]


def svg_alpha(path):
    import cairosvg
    from PIL import Image
    png = cairosvg.svg2png(url=path, output_width=SIZE, output_height=SIZE)
    return np.array(Image.open(io.BytesIO(png)).convert("RGBA"))[:, :, 3]


def stats(alpha):
    ys, xs = np.nonzero(alpha > 20)
    if len(xs) == 0:
        return {"ink": 0, "cx": None, "cy": None, "w": 0, "h": 0}
    return {"ink": int(len(xs)), "cx": float(xs.mean()) + 0.5, "cy": float(ys.mean()) + 0.5,
            "w": float(xs.max() - xs.min() + 1), "h": float(ys.max() - ys.min() + 1)}


def run_check(c, frames_cache, anim):
    def st(f):
        if f not in frames_cache:
            frames_cache[f] = render(anim, f)
        return stats(frames_cache[f])

    t = c["type"]
    if t == "centroid":
        s = st(c["frame"]); ok = s["cx"] is not None and abs(s["cx"] - c["x"]) <= c["tol"] and abs(s["cy"] - c["y"]) <= c["tol"]
        return ok, f"centroid@{c['frame']} got ({s['cx']}, {s['cy']}) want ({c['x']:.1f}, {c['y']:.1f}) ±{c['tol']}"
    if t == "alpha_max":
        m = int(frames_cache.setdefault(c["frame"], render(anim, c["frame"])).max())
        return abs(m - c["value"]) <= c["tol"], f"alpha_max@{c['frame']} got {m} want {c['value']:.0f} ±{c['tol']}"
    if t == "bbox":
        s = st(c["frame"]); ok = abs(s["w"] - c["w"]) <= c["tol"] and abs(s["h"] - c["h"]) <= c["tol"]
        return ok, f"bbox@{c['frame']} got {s['w']:.0f}x{s['h']:.0f} want {c['w']:.0f}x{c['h']:.0f} ±{c['tol']}"
    if t == "ink":
        s = st(c["frame"]); lo, hi = c["value"] * (1 - c["rel_tol"]), c["value"] * (1 + c["rel_tol"])
        return lo <= s["ink"] <= hi, f"ink@{c['frame']} got {s['ink']} want {c['value']:.0f} (±{c['rel_tol']*100:.0f}%)"
    if t == "ink_ratio":
        a, b = st(c["frame"])["ink"], max(st(c["ref"])["ink"], 1)
        return abs(a / b - c["value"]) <= c["tol"], f"ink ratio {c['frame']}/{c['ref']} got {a / b:.3f} want {c['value']} ±{c['tol']}"
    if t == "moved":
        a, b = st(c["frame_a"]), st(c["frame_b"])
        if a["cx"] is None or b["cx"] is None:
            return False, f"moved {c['frame_a']}->{c['frame_b']}: nothing drawn"
        d = math.hypot(a["cx"] - b["cx"], a["cy"] - b["cy"])
        return d >= c["min_dist"], f"moved {c['frame_a']}->{c['frame_b']} by {d:.1f}px, want >= {c['min_dist']}"
    return False, f"unknown check {t}"


def main(d):
    failures, characterizations = 0, []
    for expect_path in sorted(glob.glob(os.path.join(d, "*.expect.json"))):
        name = os.path.basename(expect_path)[: -len(".expect.json")]
        exp = json.load(open(expect_path))
        try:
            anim = LottieAnimation.from_tgs(os.path.join(d, name + ".tgs"))
        except Exception as e:  # file could not even be loaded
            print(f"FAIL {name}: rlottie could not load it: {e}"); failures += 1; continue
        cache, results = {}, []
        for c in exp["checks"]:
            results.append(run_check(c, cache, anim))
        for f in exp.get("iou_frames", []):
            svg = os.path.join(d, f"{name}.f{f}.svg")
            a, b = render(anim, f) > 20, svg_alpha(svg) > 20
            union = int((a | b).sum()); iou = (int((a & b).sum()) / union) if union else 1.0
            results.append((iou >= exp["min_iou"], f"IoU(rlottie, our renderer)@{f} = {iou:.3f} want >= {exp['min_iou']}"))
        if exp.get("characterization"):
            characterizations.append((name, all(ok for ok, _ in results)))
            print(f"INFO {name}: invalid keyframes are {'ACCEPTED' if all(ok for ok, _ in results) else 'dropped'} by this rlottie build")
            for ok, msg in results: print(f"       {msg}")
            continue
        bad = [m for ok, m in results if not ok]
        print(f"{'PASS' if not bad else 'FAIL'} {name}: {len(results)} checks")
        for m in bad: print(f"       x {m}")
        failures += bool(bad)
    print(f"\n{failures} fixture(s) failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "export/build/rlottie-fixtures"))

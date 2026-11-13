package ir.sina.tgsstudio.renderer

import ir.sina.tgsstudio.document.Layer
import ir.sina.tgsstudio.document.json.JsonObj

/** Lottie transform maths shared by the renderer and the editor. */
object Transforms {
    /** T(p) · R(r) · Skew · S(s) · T(-a) for a layer `ks` block or a shape `tr` item. */
    fun of(ks: JsonObj?, frame: Double): Affine {
        if (ks == null) return Affine.IDENTITY
        val a = PropEval.values(ks.obj("a"), frame)
        val s = PropEval.values(ks.obj("s"), frame)
        val pos = position(ks.obj("p"), frame)
        val rot = PropEval.number(ks.obj("r"), frame, 0.0)
        val sk = PropEval.number(ks.obj("sk"), frame, 0.0)
        val sa = PropEval.number(ks.obj("sa"), frame, 0.0)
        val ax = a?.getOrNull(0) ?: 0.0; val ay = a?.getOrNull(1) ?: 0.0
        val sx = (s?.getOrNull(0) ?: 100.0) / 100.0; val sy = (s?.getOrNull(1) ?: 100.0) / 100.0
        return Affine.translate(pos.first, pos.second) * Affine.rotate(rot) * Affine.skew(sk, sa) *
            Affine.scale(sx, sy) * Affine.translate(-ax, -ay)
    }

    fun position(p: JsonObj?, frame: Double): Pair<Double, Double> {
        if (p == null) return 0.0 to 0.0
        if (p.has("x") && p.has("y")) return PropEval.number(p.obj("x"), frame, 0.0) to PropEval.number(p.obj("y"), frame, 0.0)
        val v = PropEval.values(p, frame)
        return (v?.getOrNull(0) ?: 0.0) to (v?.getOrNull(1) ?: 0.0)
    }

    /** Matrix of [layer]'s parents only (identity when it has none). Cycles are cut. */
    fun layerParents(layers: List<Layer>, layer: Layer, frame: Double): Affine {
        val byIndex = HashMap<Int, Layer>()
        layers.forEach { l -> l.index?.let { byIndex.putIfAbsent(it, l) } }
        val chain = ArrayList<Layer>()
        var cur: Layer? = layer.parentIndex?.let(byIndex::get)
        val seen = HashSet<Int>()
        layer.index?.let(seen::add)
        while (cur != null && chain.size < 64) {
            chain += cur
            val p = cur.parentIndex ?: break
            if (!seen.add(p)) break
            cur = byIndex[p]
        }
        var m = Affine.IDENTITY
        for (c in chain.asReversed()) m *= of(c.raw.obj("ks"), frame)
        return m
    }

    /** Parents' matrix times the layer's own transform. */
    fun layerFull(layers: List<Layer>, layer: Layer, frame: Double): Affine =
        layerParents(layers, layer, frame) * of(layer.raw.obj("ks"), frame)
}

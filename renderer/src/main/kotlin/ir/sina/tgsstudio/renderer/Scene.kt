package ir.sina.tgsstudio.renderer

import ir.sina.tgsstudio.document.LayerRef

/** Frame-independent output of the renderer: plain data the UI layer can draw with any canvas. */
class Rgba(val r: Double, val g: Double, val b: Double, val a: Double)

class GradientStop(val position: Double, val color: Rgba)

class Gradient(val radial: Boolean, val x0: Double, val y0: Double, val x1: Double, val y1: Double, val stops: List<GradientStop>)

/** Either [color] or [gradient]. [opacity] is 0..1 and already includes group opacities. */
class Paint(val color: Rgba?, val gradient: Gradient?, val opacity: Double)

enum class Cap { BUTT, ROUND, SQUARE }
enum class Join { MITER, ROUND, BEVEL }

class StrokeStyle(
    val width: Double, val cap: Cap, val join: Join, val miterLimit: Double,
    val dashes: DoubleArray?, val dashPhase: Double,
)

/** One fill or one stroke over a set of contours. [matrix] maps contour space into the owning layer's space. */
class PaintItem(
    val contours: List<Contour>,
    val matrix: Affine,
    val paint: Paint,
    val stroke: StrokeStyle?,   // null = fill
    val evenOdd: Boolean,
    /** Index path of the fill/stroke item inside the layer's shapes (for hit testing); empty for solids. */
    val shapePath: List<Int> = emptyList(),
)

class SceneMask(val contours: List<Contour>, val inverted: Boolean)

/** [inverted] flips an alpha matte (Lottie tt = 2). */
class Matte(val inverted: Boolean, val layer: SceneLayer)

class SceneLayer(
    val ref: LayerRef,
    val name: String,
    /** Full matrix into the parent space of the composition (own transform + layer parents). */
    val matrix: Affine,
    val opacity: Double,
    val items: List<PaintItem>,
    val children: List<SceneLayer>,
    val masks: List<SceneMask>,
    val matte: Matte?,
)

class Scene(val width: Int, val height: Int, val layers: List<SceneLayer>, val unsupported: Set<String>)

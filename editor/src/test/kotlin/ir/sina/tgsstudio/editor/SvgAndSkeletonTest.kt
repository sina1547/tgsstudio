package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.renderer.Affine
import kotlin.math.hypot
import kotlin.test.*

class SvgAndSkeletonTest {
    @Test fun pathDataAllCommands() {
        val c = SvgPath.parse("M10 10 h20 v20 H10 Z m5 5 l10 0 l0 10 z")
        assertEquals(2, c.size); assertTrue(c[0].closed && c[1].closed)
        near(80.0, c[0].length, 1e-6)
        val rel = SvgPath.parse("m10,10 20,0 0,20")      // implicit line-to after moveto
        assertEquals(2, rel[0].segments.size)
        val compact = SvgPath.parse("M0 0l-1.5-2.5.5.5")  // numbers glued by sign and dot
        assertEquals(2, compact[0].segments.size)
    }

    @Test fun arcsAndCurves() {
        val half = SvgPath.parse("M0 0 A50 50 0 0 1 100 0")[0]
        near(Math.PI * 50, half.length, 0.3)
        val last = half.segments.last(); near(100.0, last.x3, 1e-6); near(0.0, last.y3, 1e-6)
        val compactFlags = SvgPath.parse("M0 0a50 50 0 1150 0")
        assertEquals(1, compactFlags.size)                                           // "1150" = flags 1,1 then x=50
        assertTrue(SvgPath.parse("M0 0 Q50 50 100 0 T200 0")[0].segments.size == 2)
        assertTrue(SvgPath.parse("M0 0 C10 0 20 10 30 10 S50 0 60 0")[0].segments.size == 2)
        assertFailsWith<IllegalArgumentException> { SvgPath.parse("M0 0 X5") }
    }

    private val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <defs><linearGradient id="g"><stop offset="0" stop-color="#ff0000"/></linearGradient></defs>
          <g transform="translate(10 10)" fill="#00f">
            <rect id="box" x="0" y="0" width="40" height="20" rx="4" style="stroke:#000;stroke-width:2"/>
            <circle cx="60" cy="10" r="10" fill="url(#g)" opacity="0.5"/>
          </g>
          <polygon points="0,50 50,50 25,90" fill="rgb(0,128,0)" fill-rule="evenodd"/>
          <text x="1" y="1">hi</text>
        </svg>"""

    @Test fun importsShapesStylesTransformsAndReportsTheRest() {
        val r = SvgImport.parse(svg, targetSize = 200.0)
        assertEquals(3, r.groups.size)
        assertTrue("<text>" in r.skipped); assertTrue(r.skipped.any { it.startsWith("gradients") })
        // later SVG elements paint on top, so they come first in a Lottie group list: polygon, circle, rect
        assertEquals(listOf("Polygon", "Circle", "box"), r.groups.map { it.string("nm") })
        val doc = Creator.blankDocument("t")
        val layer = Creator.shapeLayer(doc, doc.root.arr("layers"), "svg", 256.0, 256.0, *r.groups.toTypedArray())
        Creator.insertLayer(doc, null, layer)
        val scene = ir.sina.tgsstudio.renderer.SceneBuilder(doc).build(0.0)
        val box = Hit.bounds(scene, ir.sina.tgsstudio.document.LayerRef(null, 0))!!
        near(200.0, maxOf(box.width, box.height), 4.0)                    // fitted to the target (stroke adds a little)
        near(256.0, box.centerX, 8.0); near(256.0, box.centerY, 8.0)      // centered on the layer position
        val polygon = r.groups[0]
        assertEquals(2, polygon.arr("it")!!.items.filterIsInstance<ir.sina.tgsstudio.document.json.JsonObj>().first { it.string("ty") == "fl" }.int("r"))
    }

    @Test fun rejectsEntityTricks() {
        assertFailsWith<IllegalArgumentException> { SvgImport.parse("""<!DOCTYPE svg [<!ENTITY a "b">]><svg xmlns="http://www.w3.org/2000/svg"/>""") }
        assertFailsWith<IllegalArgumentException> { SvgImport.parse("<html/>") }
    }

    @Test fun transformParsing() {
        val m = SvgImport.parseTransform("translate(10,20) scale(2)")
        near(12.0, m.mapX(1.0, 0.0)); near(20.0, m.mapY(1.0, 0.0))
        val r = SvgImport.parseTransform("rotate(90 10 10)")
        near(10.0, r.mapX(10.0, 10.0)); near(10.0, r.mapY(10.0, 10.0)); near(20.0, r.mapX(10.0, 0.0), 1e-9)
        assertEquals(Affine.IDENTITY, SvgImport.parseTransform(""))
    }

    private fun maskOf(w: Int, h: Int, f: (Int, Int) -> Boolean) = Mask(w, h, BooleanArray(w * h) { f(it % w, it / w) })

    @Test fun skeletonOfAThickLine() {
        val m = maskOf(60, 30) { x, y -> x in 5..54 && y in 12..17 }       // 50 x 6 bar
        val c = Skeleton.centerline(m)
        assertEquals(1, c.paths.size)
        val b = c.paths[0]
        assertFalse(b.closed)
        near(6.0, c.thickness, 1.0)
        val ys = (0 until b.count).map { b.v[2 * it + 1] }
        assertTrue(ys.all { it in 13.0..17.0 }, "centerline stays inside the stroke: $ys")
        assertTrue(b.toContour().length in 42.0..52.0)
    }

    @Test fun skeletonOfARingIsAClosedLoop() {
        val m = maskOf(80, 80) { x, y -> hypot(x - 40.0, y - 40.0) in 24.0..30.0 }
        val c = Skeleton.centerline(m)
        assertEquals(1, c.paths.size); assertTrue(c.paths[0].closed)
        near(2 * Math.PI * 27, c.paths[0].toContour().length, 8.0)
    }

    @Test fun skeletonKeepsCornersAndBranches() {
        val el = maskOf(60, 60) { x, y -> (x in 10..15 && y in 10..50) || (y in 45..50 && x in 10..50) }   // an "L"
        val c = Skeleton.centerline(el)
        assertEquals(1, c.paths.size)
        // a corner vertex sits near the bend, and the two arms are straight
        val b = c.paths[0]
        assertTrue((0 until b.count).any { hypot(b.v[2 * it] - 12.5, b.v[2 * it + 1] - 47.5) < 5 })
        val tee = maskOf(70, 70) { x, y -> (y in 10..15 && x in 10..60) || (x in 32..37 && y in 10..60) }   // a "T"
        val t = Skeleton.centerline(tee)
        assertTrue(t.paths.size >= 2, "branch becomes its own path")
        val total = t.paths.sumOf { it.toContour().length }
        assertTrue(total in 85.0..115.0, "T is roughly 50 + 50 long, was $total")
    }

    @Test fun centerlineShapeBuildsStrokeOnlyGroup() {
        val m = maskOf(60, 30) { x, y -> x in 5..54 && y in 12..17 }
        val (g, size) = CenterlineShape.group(Skeleton.centerline(m), 2.0, ir.sina.tgsstudio.renderer.Rgba(1.0, 1.0, 1.0, 1.0))!!
        val types = g.arr("it")!!.items.filterIsInstance<ir.sina.tgsstudio.document.json.JsonObj>().map { it.string("ty") }
        assertEquals(listOf("sh", "st", "tr"), types)
        assertTrue(size.first > 80.0)
    }
}

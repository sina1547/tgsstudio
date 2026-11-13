package ir.sina.tgsstudio.renderer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun near(expected: Double, actual: Double, eps: Double = 1e-6, msg: String = "") =
    assertTrue(abs(expected - actual) <= eps, "$msg expected $expected but was $actual")

class GeometryTest {
    @Test fun affineComposition() {
        val m = Affine.translate(10.0, 0.0) * Affine.scale(2.0, 2.0)  // scale first, then translate
        near(12.0, m.mapX(1.0, 0.0)); near(0.0, m.mapY(1.0, 0.0))
        val r = Affine.rotate(90.0) // clockwise on a y-down screen: +x maps to +y
        near(0.0, r.mapX(1.0, 0.0)); near(1.0, r.mapY(1.0, 0.0))
    }

    @Test fun lineLengthAndSlice() {
        val line = Cubic(0.0, 0.0, 0.0, 0.0, 100.0, 0.0, 100.0, 0.0)
        near(100.0, line.length, 0.5)
        val s = line.slice(25.0, 75.0)
        near(25.0, s.x0, 0.5); near(75.0, s.x3, 0.5)
    }

    @Test fun ellipseLengthIsCircumference() {
        near(Math.PI * 100, Shapes.ellipse(0.0, 0.0, 100.0, 100.0).length, 0.5)
    }

    @Test fun roundedRectPerimeter() {
        val c = Shapes.rect(0.0, 0.0, 100.0, 60.0, 10.0)
        near(2 * (80 + 40) + 2 * Math.PI * 10, c.length, 0.2)
    }

    @Test fun bezierFromLottieVertices() {
        val b = Bezier(true, doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0), DoubleArray(6), DoubleArray(6))
        val c = b.toContour()
        assertEquals(3, c.segments.size) // closed triangle
        near(0.0, c.segments.last().x3); near(0.0, c.segments.last().y3)
    }
}

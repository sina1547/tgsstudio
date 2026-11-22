package ir.sina.tgsstudio.renderer

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** 2D affine transform: x' = a·x + c·y + e, y' = b·x + d·y + f (y axis points down, as in Lottie). */
data class Affine(
    val a: Double = 1.0, val b: Double = 0.0,
    val c: Double = 0.0, val d: Double = 1.0,
    val e: Double = 0.0, val f: Double = 0.0,
) {
    /** `this * o` applies [o] first, then this. */
    operator fun times(o: Affine) = Affine(
        a * o.a + c * o.b, b * o.a + d * o.b,
        a * o.c + c * o.d, b * o.c + d * o.d,
        a * o.e + c * o.f + e, b * o.e + d * o.f + f,
    )

    val determinant: Double get() = a * d - b * c

    /** Inverse transform, or null when the matrix is singular. */
    fun inverse(): Affine? {
        val det = determinant
        if (Math.abs(det) < 1e-12) return null
        return Affine(d / det, -b / det, -c / det, a / det, (c * f - d * e) / det, (b * e - a * f) / det)
    }

    /** Applies only the linear part (no translation). */
    fun mapVectorX(x: Double, y: Double) = a * x + c * y
    fun mapVectorY(x: Double, y: Double) = b * x + d * y

    fun mapX(x: Double, y: Double) = a * x + c * y + e
    fun mapY(x: Double, y: Double) = b * x + d * y + f

    companion object {
        val IDENTITY = Affine()
        fun translate(x: Double, y: Double) = Affine(e = x, f = y)
        fun scale(x: Double, y: Double) = Affine(a = x, d = y)
        /** Clockwise on screen for positive degrees. */
        fun rotate(deg: Double): Affine {
            val r = Math.toRadians(deg)
            return Affine(cos(r), sin(r), -sin(r), cos(r))
        }
        /** Skew along an axis, Lottie style ('sk' amount, 'sa' axis angle, both degrees). */
        fun skew(skDeg: Double, axisDeg: Double): Affine {
            if (skDeg == 0.0) return IDENTITY
            val shear = Affine(c = tan(Math.toRadians(-skDeg)))
            return rotate(axisDeg) * shear * rotate(-axisDeg)
        }
    }
}

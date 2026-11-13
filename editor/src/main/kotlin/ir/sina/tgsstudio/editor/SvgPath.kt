package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.renderer.Contour
import ir.sina.tgsstudio.renderer.Cubic
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** SVG path data (the `d` attribute) to cubic contours. Supports every command, relative and absolute. */
object SvgPath {
    fun parse(d: String): List<Contour> {
        val out = ArrayList<Contour>()
        val tok = Tokens(d)
        var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0
        var segs = ArrayList<Cubic>()
        var lastCtrlX = 0.0; var lastCtrlY = 0.0; var lastCmd = ' '
        fun flush(closed: Boolean) { if (segs.isNotEmpty()) out += Contour(segs, closed); segs = ArrayList() }
        fun line(x: Double, y: Double) { segs += Cubic(cx, cy, cx, cy, x, y, x, y); cx = x; cy = y }
        fun cubic(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) {
            segs += Cubic(cx, cy, x1, y1, x2, y2, x, y); lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
        }
        var cmd = ' '
        while (true) {
            val c = tok.command() ?: if (tok.hasNumber() && cmd != ' ') cmd else break
            if (c != cmd || c.lowercaseChar() == 'z') cmd = c
            val rel = cmd.isLowerCase()
            val ox = if (rel) cx else 0.0; val oy = if (rel) cy else 0.0
            when (cmd.lowercaseChar()) {
                'm' -> {
                    flush(false)
                    val x = tok.num() + ox; val y = tok.num() + oy
                    cx = x; cy = y; sx = x; sy = y
                    cmd = if (rel) 'l' else 'L'   // further pairs are implicit line-to
                }
                'l' -> line(tok.num() + ox, tok.num() + oy)
                'h' -> line(tok.num() + ox, cy)
                'v' -> line(cx, tok.num() + oy)
                'c' -> cubic(tok.num() + ox, tok.num() + oy, tok.num() + ox, tok.num() + oy, tok.num() + ox, tok.num() + oy)
                's' -> {
                    val x2 = tok.num() + ox; val y2 = tok.num() + oy; val x = tok.num() + ox; val y = tok.num() + oy
                    val smooth = lastCmd.lowercaseChar() == 'c' || lastCmd.lowercaseChar() == 's'
                    cubic(if (smooth) 2 * cx - lastCtrlX else cx, if (smooth) 2 * cy - lastCtrlY else cy, x2, y2, x, y)
                }
                'q' -> { val qx = tok.num() + ox; val qy = tok.num() + oy; val x = tok.num() + ox; val y = tok.num() + oy; quad(::cubic, cx, cy, qx, qy, x, y); lastCtrlX = qx; lastCtrlY = qy }
                't' -> {
                    val x = tok.num() + ox; val y = tok.num() + oy
                    val smooth = lastCmd.lowercaseChar() == 'q' || lastCmd.lowercaseChar() == 't'
                    val qx = if (smooth) 2 * cx - lastCtrlX else cx; val qy = if (smooth) 2 * cy - lastCtrlY else cy
                    quad(::cubic, cx, cy, qx, qy, x, y); lastCtrlX = qx; lastCtrlY = qy
                }
                'a' -> {
                    val rx = tok.num(); val ry = tok.num(); val rot = tok.num(); val large = tok.flag(); val sweep = tok.flag()
                    val x = tok.num() + ox; val y = tok.num() + oy
                    for (s in arc(cx, cy, rx, ry, rot, large, sweep, x, y)) segs += s
                    cx = x; cy = y
                }
                'z' -> {
                    if (hypot(cx - sx, cy - sy) > 1e-9) line(sx, sy)
                    flush(true); cx = sx; cy = sy
                }
                else -> throw IllegalArgumentException("Unknown path command '$cmd'")
            }
            lastCmd = cmd
        }
        flush(false)
        return out
    }

    private fun quad(emit: (Double, Double, Double, Double, Double, Double) -> Unit, x0: Double, y0: Double, qx: Double, qy: Double, x: Double, y: Double) =
        emit(x0 + 2.0 / 3 * (qx - x0), y0 + 2.0 / 3 * (qy - y0), x + 2.0 / 3 * (qx - x), y + 2.0 / 3 * (qy - y), x, y)

    /** Endpoint arc to cubic segments (SVG implementation notes F.6.5). */
    fun arc(x0: Double, y0: Double, rxIn: Double, ryIn: Double, rotDeg: Double, large: Boolean, sweep: Boolean, x: Double, y: Double): List<Cubic> {
        var rx = abs(rxIn); var ry = abs(ryIn)
        if ((x0 == x && y0 == y)) return emptyList()
        if (rx == 0.0 || ry == 0.0) return listOf(Cubic(x0, y0, x0, y0, x, y, x, y))
        val phi = Math.toRadians(rotDeg); val cp = cos(phi); val sp = sin(phi)
        val dx = (x0 - x) / 2; val dy = (y0 - y) / 2
        val x1p = cp * dx + sp * dy; val y1p = -sp * dx + cp * dy
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1) { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val co = (if (large == sweep) -1 else 1) * sqrt(maxOf(0.0, num / den))
        val cxp = co * rx * y1p / ry; val cyp = -co * ry * x1p / rx
        val cx = cp * cxp - sp * cyp + (x0 + x) / 2; val cy = sp * cxp + cp * cyp + (y0 + y) / 2
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = atan2(ux * vy - uy * vx, ux * vx + uy * vy); return a
        }
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * Math.PI else if (sweep && delta < 0) delta += 2 * Math.PI
        val n = ceil(abs(delta) / (Math.PI / 2) - 1e-9).toInt().coerceAtLeast(1)
        val step = delta / n; val k = 4.0 / 3 * tan(step / 4)
        val out = ArrayList<Cubic>(n)
        var a = theta1
        var px = x0; var py = y0
        fun pt(t: Double, dt: Boolean): Pair<Double, Double> {
            val ex = if (dt) -rx * sin(t) else rx * cos(t); val ey = if (dt) ry * cos(t) else ry * sin(t)
            return if (dt) (cp * ex - sp * ey) to (sp * ex + cp * ey) else (cp * ex - sp * ey + cx) to (sp * ex + cp * ey + cy)
        }
        for (i in 0 until n) {
            val b = a + step
            val (ex, ey) = if (i == n - 1) x to y else pt(b, false)
            val (d0x, d0y) = pt(a, true); val (d1x, d1y) = pt(b, true)
            out += Cubic(px, py, px + k * d0x, py + k * d0y, ex - k * d1x, ey - k * d1y, ex, ey)
            px = ex; py = ey; a = b
        }
        return out
    }

    private class Tokens(val s: String) {
        var i = 0
        private fun skip() { while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++ }
        fun command(): Char? { skip(); return if (i < s.length && s[i].isLetter() && s[i] != 'e' && s[i] != 'E') s[i++] else null }
        fun hasNumber(): Boolean { skip(); return i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.') }
        fun flag(): Boolean { skip(); require(i < s.length && (s[i] == '0' || s[i] == '1')) { "Bad arc flag" }; return s[i++] == '1' }
        fun num(): Double {
            skip()
            val st = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') { i++; while (i < s.length && s[i].isDigit()) i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                val save = i; i++
                if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                if (i < s.length && s[i].isDigit()) while (i < s.length && s[i].isDigit()) i++ else i = save
            }
            require(i > st) { "Expected a number at offset $st" }
            return s.substring(st, i).toDouble()
        }
    }
}

package ir.sina.tgsstudio.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class TrimTest {
    private val line = Contour(listOf(Cubic(0.0, 0.0, 0.0, 0.0, 100.0, 0.0, 100.0, 0.0)), false)
    private val line2 = Contour(listOf(Cubic(0.0, 10.0, 0.0, 10.0, 100.0, 10.0, 100.0, 10.0)), false)

    @Test fun middleHalf() {
        val r = Trim.apply(listOf(line), 25.0, 75.0, 0.0, true)
        assertEquals(1, r.size)
        near(50.0, r[0].length, 0.5); near(25.0, r[0].segments.first().x0, 0.5)
    }

    @Test fun fullAndEmpty() {
        assertEquals(1, Trim.apply(listOf(line), 0.0, 100.0, 0.0, true).size)
        assertEquals(0, Trim.apply(listOf(line), 40.0, 40.0, 0.0, true).size)
    }

    @Test fun offsetWrapsIntoTwoPieces() {
        // 50% window shifted by 270° (= 75% of the length): covers 75..100 and 0..25
        val r = Trim.apply(listOf(line), 0.0, 50.0, 270.0, true)
        assertEquals(2, r.size)
        near(50.0, r.sumOf { it.length }, 0.5)
    }

    @Test fun individuallyTreatsPathsAsOne() {
        val simul = Trim.apply(listOf(line, line2), 0.0, 50.0, 0.0, true)
        val indiv = Trim.apply(listOf(line, line2), 0.0, 50.0, 0.0, false)
        near(100.0, simul.sumOf { it.length }, 1.0)   // 50 + 50
        near(100.0, indiv.sumOf { it.length }, 1.0)   // all of the first path, none of the second
        assertEquals(2, simul.size); assertEquals(1, indiv.size)
    }

    @Test fun reversedStartEndAreSwapped() {
        near(50.0, Trim.apply(listOf(line), 75.0, 25.0, 0.0, true)[0].length, 0.5)
    }
}

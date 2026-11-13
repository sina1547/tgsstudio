package ir.sina.tgsstudio.renderer

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropEvalTest {
    private fun prop(json: String) = JsonParser.parse(json) as JsonObj

    @Test fun staticValues() {
        near(5.0, PropEval.number(prop("""{"a":0,"k":5}"""), 0.0, 0.0))
        assertEquals(3, PropEval.values(prop("""{"a":0,"k":[1,2,3]}"""), 0.0)!!.size)
        assertNull(PropEval.values(null, 0.0))
        near(7.0, PropEval.number(null, 0.0, 7.0))
    }

    private val linear = prop("""{"a":1,"k":[{"t":10,"s":[0],"e":[100]},{"t":20,"s":[100]}]}""")

    @Test fun linearKeyframesAndClamping() {
        near(0.0, PropEval.number(linear, 0.0, -1.0))      // before the first keyframe
        near(50.0, PropEval.number(linear, 15.0, -1.0))
        near(100.0, PropEval.number(linear, 99.0, -1.0))   // after the last
    }

    @Test fun endValueFromNextKeyframeWhenMissing() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[10]}]}""")
        near(5.0, PropEval.number(p, 5.0, -1.0))
    }

    @Test fun holdKeyframe() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[1],"h":1},{"t":10,"s":[9]}]}""")
        near(1.0, PropEval.number(p, 9.0, -1.0))
    }

    @Test fun easeInOutIsSymmetricAndSlowAtTheEnds() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[0],"i":{"x":[0.667],"y":[1]},"o":{"x":[0.333],"y":[0]}},{"t":100,"s":[100]}]}""")
        near(50.0, PropEval.number(p, 50.0, -1.0), 0.5)
        assertTrue(PropEval.number(p, 10.0, -1.0) < 10.0)  // slower than linear at the start
        assertTrue(PropEval.number(p, 90.0, -1.0) > 90.0)  // and at the end
    }

    @Test fun perDimensionEasing() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[0,0],"i":{"x":[1,0.5],"y":[1,0.5]},"o":{"x":[0,0.5],"y":[0,0.5]}},{"t":10,"s":[10,10]}]}""")
        val v = PropEval.values(p, 5.0)!!
        near(5.0, v[0], 1e-3); assertEquals(2, v.size)
    }

    @Test fun spatialMotionPathBendsAndHitsEnds() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[0,0],"e":[100,0],"to":[0,50],"ti":[0,50]},{"t":10,"s":[100,0]}]}""")
        val mid = PropEval.values(p, 5.0)!!
        near(50.0, mid[0], 1.0); assertTrue(mid[1] > 20.0, "path should bulge away from the straight line")
        near(0.0, PropEval.values(p, 0.0)!![1]); near(0.0, PropEval.values(p, 10.0)!![1], 1e-6)
    }

    @Test fun pathKeyframesInterpolateVertices() {
        fun pth(x: Int) = """{"c":true,"i":[[0,0],[0,0]],"o":[[0,0],[0,0]],"v":[[0,0],[$x,0]]}"""
        val p = prop("""{"a":1,"k":[{"t":0,"s":[${pth(10)}],"e":[${pth(30)}]},{"t":10,"s":[${pth(30)}]}]}""")
        val b = assertNotNull(PropEval.path(p, 5.0))
        near(20.0, b.v[2])
        assertNotNull(PropEval.path(prop("""{"a":0,"k":${pth(5)}}"""), 0.0))
    }
}

package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.renderer.PropEval
import kotlin.test.*

class PropAndKeyframeTest {
    private fun prop(json: String) = JsonParser.parse(json) as JsonObj

    @Test fun staticWriteKeepsTrailingDimensions() {
        val pr = prop("""{"a":0,"k":[1,2,3]}""")
        PropWrite.setValues(pr, 0.0, doubleArrayOf(10.0, 20.0))
        assertEquals(listOf(10.0, 20.0, 3.0), PropEval.values(pr, 0.0)!!.toList())
        val sc = prop("""{"a":0,"k":5}""")
        PropWrite.setValues(sc, 0.0, doubleArrayOf(7.0))
        assertEquals(ir.sina.tgsstudio.document.json.JsonNum(7L), sc["k"])  // stays a plain number
    }

    @Test fun animatedWriteUpdatesOrInsertsKeyframeInheritingEasing() {
        val pr = prop("""{"a":1,"k":[{"t":0,"s":[0],"i":{"x":[0.6],"y":[1]},"o":{"x":[0.3],"y":[0]}},{"t":100,"s":[100]}]}""")
        PropWrite.setValues(pr, 100.0, doubleArrayOf(50.0))   // existing keyframe -> updated
        assertEquals(2, KeyframeOps.list(pr).size)
        near(50.0, PropEval.number(pr, 100.0, -1.0))
        PropWrite.setValues(pr, 50.0, doubleArrayOf(80.0))     // new keyframe
        assertEquals(listOf(0.0, 50.0, 100.0), KeyframeOps.times(pr))
        near(80.0, PropEval.number(pr, 50.0, -1.0))
        assertNotNull(KeyframeOps.easing(pr, 1))              // inherited from the previous keyframe
    }

    @Test fun oldFormatEndValuesStayConsistent() {
        val pr = prop("""{"a":1,"k":[{"t":0,"s":[0],"e":[100]},{"t":10,"s":[100]}]}""")
        PropWrite.setValues(pr, 5.0, doubleArrayOf(70.0))
        val kfs = KeyframeOps.list(pr)
        assertEquals("[70]", kfs[0]["e"].toString()); assertFalse(kfs.last().has("e"))
    }

    @Test fun addRemoveKeyframes() {
        val pr = prop("""{"a":0,"k":[5,6]}""")
        assertEquals(0, KeyframeOps.addAt(pr, 10.0))
        assertTrue(PropWrite.isAnimated(pr)); assertEquals(1, pr.int("a"))
        KeyframeOps.addAt(pr, 30.0)
        PropWrite.setValues(pr, 30.0, doubleArrayOf(50.0, 60.0))
        assertEquals(listOf(10.0, 30.0), KeyframeOps.times(pr))
        near(27.5, PropEval.values(pr, 20.0)!![0], 1e-6)
        KeyframeOps.remove(pr, 1); KeyframeOps.remove(pr, 0)
        assertFalse(PropWrite.isAnimated(pr)); assertEquals(0, pr.int("a"))
        assertEquals(listOf(5.0, 6.0), PropEval.values(pr, 0.0)!!.toList())
    }

    @Test fun moveKeyframeKeepsOrderAndRefusesCollisions() {
        val pr = prop("""{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[1]},{"t":20,"s":[2]}]}""")
        assertEquals(1, KeyframeOps.move(pr, 0, 15.0))
        assertEquals(listOf(10.0, 15.0, 20.0), KeyframeOps.times(pr))
        assertEquals(0, KeyframeOps.move(pr, 0, 15.0))         // occupied
    }

    @Test fun interpolationModes() {
        val pr = prop("""{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[10]}]}""")
        assertEquals(Interp.LINEAR, KeyframeOps.interpolation(pr, 0))
        KeyframeOps.setInterpolation(pr, 0, Interp.HOLD)
        assertEquals(Interp.HOLD, KeyframeOps.interpolation(pr, 0)); near(0.0, PropEval.number(pr, 9.0, -1.0))
        KeyframeOps.setInterpolation(pr, 0, Interp.BEZIER)
        assertEquals(Interp.BEZIER, KeyframeOps.interpolation(pr, 0)); assertTrue(PropEval.number(pr, 2.0, -1.0) < 2.0)
        KeyframeOps.setEasing(pr, 0, Easing(0.0, 0.0, 0.58, 1.0))
        assertEquals(Easing(0.0, 0.0, 0.58, 1.0), KeyframeOps.easing(pr, 0))
        KeyframeOps.setInterpolation(pr, 0, Interp.LINEAR)
        near(2.0, PropEval.number(pr, 2.0, -1.0))
    }

    @Test fun copyPaste() {
        val a = prop("""{"a":1,"k":[{"t":0,"s":[5],"h":1},{"t":10,"s":[9]}]}""")
        val copied = KeyframeOps.copy(a, 0)!!
        val b = prop("""{"a":1,"k":[{"t":0,"s":[1]},{"t":10,"s":[2]}]}""")
        KeyframeOps.paste(b, copied, 5.0)
        assertEquals(listOf(0.0, 5.0, 10.0), KeyframeOps.times(b))
        assertEquals(Interp.HOLD, KeyframeOps.interpolation(b, 1))
    }

    @Test fun pathKeyframesRoundTrip() {
        val b1 = ir.sina.tgsstudio.renderer.Bezier(true, doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0), DoubleArray(6), DoubleArray(6))
        val pr = staticPathProp(b1)
        assertEquals(0, KeyframeOps.addAt(pr, 0.0, isPath = true))
        PropWrite.setPath(pr, 10.0, PathOps.moveVertex(b1, 1, 30.0, 0.0))
        near(20.0, PropEval.path(pr, 5.0)!!.v[2])
    }
}

package com.arcanatable

import org.junit.Assert.*
import org.junit.Test

/** Owner's Studio log, Nov 7: nine "+ Width" taps on a hand button did nothing. Every + and − tap must change it. */
class StudioHandSteppersTest {
    private fun pass(x:Float,w:Float)=StudioElement("pass","button","PASS",x,344f,w,44f,strong=true)

    @Test fun plusGrowsInPlaceWhenThereIsRoom() {
        val e=StudioHandEditing.widen(pass(8f,24f),16f,412f)
        assertEquals(8f,e.x,0f);assertEquals(40f,e.w,0f)
    }

    @Test fun plusAgainstTheRightEdgeGrowsLeftInsteadOfDoingNothing() {
        var e=pass(388f,24f) // right edge exactly at 412
        val widths=mutableListOf<Float>()
        repeat(9){e=StudioHandEditing.widen(e,16f,412f);widths+=e.w}
        assertEquals("nine taps, nine changes: $widths",9,widths.distinct().size)
        assertEquals(412f,e.x+e.w,0.01f)
    }

    @Test fun plusOnAButtonAlreadyPastTheEdgeStillGrowsAndPullsItBackIn() {
        val e=StudioHandEditing.widen(pass(400f,24f),16f,412f)
        assertEquals(40f,e.w,0f);assertEquals(372f,e.x,0f)
    }

    @Test fun plusStopsAtTheFullWidthAndMinusStopsAt24() {
        assertEquals(412f,StudioHandEditing.widen(pass(0f,410f),16f,412f).w,0f)
        assertEquals(24f,StudioHandEditing.widen(pass(8f,30f),-16f,412f).w,0f)
        assertEquals(8f,StudioHandEditing.widen(pass(8f,30f),-16f,412f).x,0f)
    }
}

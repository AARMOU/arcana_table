package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/** Owner, Nov 8: clear boards with solid cards, and opponents' copies of Pass can be hidden. */
class StudioBackdropTest {
    @Test fun backgroundFadesOnlyTheColourAndRoundTrips() {
        val style=StudioControlStyle(backdrop=0f,alpha=null)
        assertEquals(0f,Color(0xFF203040).studioBackdrop(style.backdrop).alpha,0.001f)
        assertEquals(0.5f,Color(0xFF203040).studioBackdrop(0.5f).alpha,0.01f)
        assertEquals("solid when unset",Color(0xFF203040),Color(0xFF203040).studioBackdrop(null))
        assertNull("the whole item is untouched",style.alpha)
        assertEquals(0.3f,StudioControlStyle.parse(StudioControlStyle(backdrop=0.3f).json()).backdrop!!,0.001f)
        assertTrue(StudioControlStyle(backdrop=1.5f).problems().isNotEmpty())
    }

    @Test fun opponentCopiesOfPassCanBeHiddenButYoursAndTheWayBackCannot() {
        val site=studioStableId("TableBoardViews.Panel.1","Table",0).substringBeforeLast(":")
        fun action(seat:String?,title:String)=StudioLiveControl(if(seat==null)"$site:0" else "$site@$seat:0","Table","action",title,true)
        assertFalse(action("opp1","PASS TURN").requiredNativeAction)
        assertFalse(action("opp2","CONFIRM").requiredNativeAction)
        assertTrue(action("you","PASS TURN").requiredNativeAction)
        assertTrue(action(null,"PASS TURN").requiredNativeAction)
        assertTrue("the way back stays even on an opponent's board",action("opp1","BACK").requiredNativeAction)
    }
}

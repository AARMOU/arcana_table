package com.arcanatable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner note 28 / Goose J8: per-player edits belong to the player (you, opponent 1…), not to their order on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class StudioSeatIdsTest {
    private fun prefs()=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("seats-${System.nanoTime()}",0)

    @Test fun seatKeysCountOpponentsFromYourSeat() {
        assertEquals("you",studioSeatKey(2,2,4))
        assertEquals(listOf("opp1","opp2","opp3"),listOf(3,0,1).map{studioSeatKey(it,2,4)})
        // A seat added at the end keeps everyone else's key when you sit first.
        assertEquals(listOf("you","opp1"),(0..1).map{studioSeatKey(it,0,2)})
        assertEquals(listOf("you","opp1","opp2","opp3"),(0..3).map{studioSeatKey(it,0,4)})
        assertEquals("seat2",studioSeatKey(1,-1,3))
    }

    @Test fun oldScreenOrderEditShowsUntilEditedThenMovesToThePlayer() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        val base=studioStableId("ArgentumUi.Text.1","Table",0).substringBeforeLast(":")
        val legacy="$base:1";val mine="$base@opp1:0"
        runtime.record(StudioLiveControl(legacy,"Table","data","AI",true),null)
        runtime.begin();runtime.edit(legacy,StudioControlStyle(dx=12f));runtime.apply()
        // The same chip after the update: its own player ID, with the screen-order ID as its old name.
        runtime.alias(mine,legacy)
        runtime.record(StudioLiveControl(mine,"Table","data","AI",true),null)
        assertEquals("the saved edit still shows",12f,runtime.style(mine).dx)
        runtime.begin();runtime.edit(mine,runtime.style(mine).copy(dy=4f))
        assertEquals(12f,runtime.draft.controls[mine]?.dx);assertEquals(4f,runtime.draft.controls[mine]?.dy)
        assertFalse("the old screen-order edit is moved, not copied",runtime.draft.controls.containsKey(legacy))
        assertEquals("Table",runtime.draft.owners[mine]);assertFalse(runtime.draft.owners.containsKey(legacy))
        assertTrue(runtime.problems().isEmpty())
    }

    @Test fun playerIdsPassTheProjectParser() {
        val id=studioStableId("ArgentumUi.Text.1","Table",0).substringBeforeLast(":")+"@opp2:0"
        val p=StudioControlProject(controls=mapOf(id to StudioControlStyle(dx=3f)),owners=mapOf(id to "Table"))
        assertEquals(3f,StudioControlProject.parse(p.json()).controls[id]?.dx)
    }
}

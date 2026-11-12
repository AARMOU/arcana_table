package com.arcanatable

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner, Nov 8: "the player cards in the table view don't look fixed". Tapping a card picks its tappable item. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class StudioBackdropTargetTest {
    @Test fun aPlayerCardsTappableItemEditsTheCardPanelsBackground() {
        val runtime=StudioControlsRuntime(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("bd-${System.nanoTime()}",0),"portrait")
        val card=Rect(10f,100f,400f,300f)
        val panelId=studioStableId("TableBoardViews.Panel.5","Table",0)
        val tapId=studioStableId("TableBoardViews.combinedClickable.2","Table",0)
        val nameId=studioStableId("TableBoardViews.Text.1","Table",0)
        runtime.record(StudioLiveControl(panelId,"Table","panel","Player card",true,bounds=card),null)
        runtime.record(StudioLiveControl(tapId,"Table","container-action","Player card",true,bounds=card),null)
        runtime.record(StudioLiveControl(nameId,"Table","data","AI",true,bounds=Rect(20f,110f,120f,130f)),null)
        assertEquals(panelId,studioBackdropTarget(runtime,runtime.controls[tapId]!!,StudioControlStyle()))
        assertEquals(panelId,studioBackdropTarget(runtime,runtime.controls[panelId]!!,StudioControlStyle()))
        assertNull("a name has no background of its own",studioBackdropTarget(runtime,runtime.controls[nameId]!!,StudioControlStyle()))
    }
}

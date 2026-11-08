package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arcanatable.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Owner, Nov 7: "double, triple, quadruple check that the player chips move correctly". The real seat row (TABLE chip
 * and two player chips in the Studio scrolling row, above a board), driven through Studio's own picking and edits.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h500dp-port-xhdpi")
class StudioSeatChipsUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var runtime:StudioControlsRuntime
    private var taps=0
    private val d get()=rule.density.density

    private fun render() {
        runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("seat-${System.nanoTime()}",0),"portrait").also{it.enableCatalog()}
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime,LocalStudioScreen provides "Table"){
            Column(Modifier.fillMaxSize().background(darkTableColors.background).padding(top=40.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min=44.dp).zIndex(1f)) {
                    Row(Modifier.weight(1f).studioRowScroll(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        Box{ViewChip("TABLE",selected=false,compact=true,onClick={})}
                        Box{ArgentumSeatTab(ArgentumSeatChip("You",40,true,true,true),null,null,true,{taps++},turnLabel="YOUR TURN")}
                        Box{ArgentumSeatTab(ArgentumSeatChip("Offline AI",39,false,false,false),null,null,false,{})}
                    }
                }
                Box(Modifier.fillMaxWidth().height(200.dp).background(darkTableColors.panel))
            }
        }}}
        rule.waitForIdle();rule.runOnIdle{runtime.begin()};rule.waitForIdle()
    }
    private fun chips()=runtime.controls.values.filter{it.kind=="container-action" && it.id.contains("ArgentumUi.clickable.1")}.sortedBy{it.bounds.left}
    private fun named(t:String)=runtime.controls.values.single{it.title==t && !it.isAction}
    private fun edit(id:String,s:StudioControlStyle){rule.runOnIdle{runtime.edit(id,s)};rule.waitForIdle()}

    @Test fun aTapOnTheChipOrItsNamePicksTheWholeChipAndItIsNamedAfterThePlayer() {
        render()
        val you=chips()[0]
        assertEquals(listOf("YOU","OFFLINE AI"),chips().map{it.title})
        assertEquals(you.id,StudioEditorGeometry.hitTest(runtime.controls.values,named("YOU").bounds.center,you.window){true}?.id)
        assertEquals(you.id,StudioEditorGeometry.hitTest(runtime.controls.values,named("40").bounds.center,you.window){true}?.id)
        assertEquals("a second tap goes inside",named("40").id,
            StudioEditorGeometry.hitTest(runtime.controls.values,named("40").bounds.center,you.window,you.id){true}?.id)
    }

    @Test fun movingAChipMovesEverythingInsideItByExactlyTheSameAmountAndKeepsItsName() {
        render()
        val you=chips()[0];val inside=listOf("YOU","YOUR TURN","40").associateWith{named(it).bounds}
        edit(you.id,StudioControlStyle(dx=24f,dy=16f))
        val now=runtime.controls[you.id]!!
        assertEquals(24f*d,now.bounds.left-you.bounds.left,1f);assertEquals(16f*d,now.bounds.top-you.bounds.top,1f)
        assertEquals(you.bounds.width,now.bounds.width,1f);assertEquals(you.bounds.height,now.bounds.height,1f)
        inside.forEach{(t,b)->val n=named(t).bounds;assertEquals("$t x",24f*d,n.left-b.left,1f);assertEquals("$t y",16f*d,n.top-b.top,1f)}
        assertEquals("YOU",now.title)
        assertEquals("the other chip stays put",chips()[1].bounds.left,runtime.controls[chips()[1].id]!!.bounds.left,0f)
    }

    @Test fun resizingAChipGivesExactlyThatSizeAndKeepsItsName() {
        render()
        val you=chips()[0]
        edit(you.id,StudioControlStyle(width=220f,height=100f))
        val b=runtime.controls[you.id]!!.bounds
        assertEquals(220f*d,b.width,1f);assertEquals(100f*d,b.height,1f)
        assertEquals("YOU",runtime.controls[you.id]!!.title)
        assertTrue("the words stay inside",listOf("YOU","YOUR TURN","40").all{b.contains(named(it).bounds.center)})
    }

    @Test fun holdPicksBothChipsAndOneDragMovesBoth() {
        render()
        val before=chips().associate{it.id to it.bounds}
        rule.runOnIdle{runtime.selected=chips()[0].id;runtime.holdSelect(chips()[0].id)}
        assertEquals(chips().map{it.id}.toSet(),runtime.members().toSet())
        rule.runOnIdle{runtime.editGroup(runtime.style(chips()[0].id).copy(dy=20f),d)};rule.waitForIdle()
        chips().forEach{assertEquals(it.title,20f*d,it.bounds.top-before.getValue(it.id).top,1f)}
    }

    /** It used to vanish (the scrolling row clipped it) and couldn't be tapped; then it hid under the board. */
    @Test fun aChipMovedDownOntoTheBoardStaysVisibleAndTappableInPlay() {
        render()
        val you=chips()[0]
        listOf(30f,60f,120f).forEach {dy->
            rule.runOnIdle{runtime.begin();runtime.edit(you.id,StudioControlStyle(dy=dy))};rule.waitForIdle()
            val b=runtime.controls[you.id]!!.bounds
            assertEquals("dy=$dy: whole chip still on screen",you.bounds.height,b.height,1f)
            rule.runOnIdle{runtime.apply()};rule.waitForIdle()
            taps=0
            rule.onRoot().performTouchInput{click(you.bounds.center.copy(y=you.bounds.center.y+dy*d))};rule.waitForIdle()
            assertEquals("dy=$dy: tapping where it now is opens that player",1,taps)
        }
    }

    @Test fun aTallerTableChipFillsItsFrameWithTheWordInTheMiddle() {
        render()
        val table=runtime.controls.values.single{it.kind=="action" && it.title=="TABLE"}
        edit(table.id,StudioControlStyle(width=140f,height=120f))
        val frame=runtime.controls[table.id]!!.bounds
        assertEquals(140f*d,frame.width,2f);assertEquals(120f*d,frame.height,2f)
        // The chip's colour reaches the top and bottom of its frame (it used to be a thin bar in the middle).
        val root=rule.activity.window.decorView.rootView
        val bmp=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        val chip=android.graphics.Color.rgb(0x26,0x33,0x2F) // the chip's own colour (raised)
        fun at(y:Float)=bmp.getPixel(frame.center.x.toInt(),y.toInt())
        assertEquals("near the top",chip,at(frame.top+10f*d))
        assertEquals("near the bottom",chip,at(frame.bottom-10f*d))
    }
}

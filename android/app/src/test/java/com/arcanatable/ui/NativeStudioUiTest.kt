package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.arcanatable.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
class NativeStudioUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val calls=mutableListOf<String>()
    private val layout=mutableStateOf(StudioLayout.reference())
    private val busy=mutableStateOf(false)
    private fun render(count:Int=7,width:Int=412,height:Int=928) {
        rule.setContent { MaterialTheme {
            Box(Modifier.width(width.dp)) {
                HandSheet((1..count).map{GameCard(it,"Real card $it","Creature",Color.DarkGray)},false,height.dp,null,false,true,true,false,emptyList(),2,
                    {},{}, {},{}, {},{calls+="pass"},{calls+="stack"},{},{calls+=it},{calls+="select:${it.id}"},{_,_->},
                    argentumMode=true,argentumPrimaryLabel="PASS → ATTACKERS",legalActionCount=3,
                    onUndo={calls+="undo"},onActions={calls+="actions"},onCast={calls+="cast:${it?.id}"},
                    castableIds=setOf(1,3),actionsBusy=busy.value,libraryCount=92,graveyardCount=3,
                    onPreview={calls+="preview:${it.id}"},studioLayout=layout.value)
            }
        } }
        rule.waitForIdle()
    }
    private fun responsiveControlsFit(width:Int,height:Int) {
        layout.value=StudioLayout.responsive(width.toFloat(),height.toFloat())
        render(24,width,height)
        val density=rule.activity.resources.displayMetrics.density
        UiTestSupport.screenshot(rule.activity,"studio-responsive-${width}x$height")
        listOf("pass","undo","actions","cast","metric0","metric1","metric2","metric3").forEach {id->
            val bounds=rule.onNodeWithTag("studio-element-$id").fetchSemanticsNode().boundsInRoot
            assertTrue("$id left $bounds density=$density",bounds.left>=0);assertTrue("$id right $bounds width=$width density=$density",bounds.right<=width*density+1)
            assertTrue("$id target",bounds.width>=44*density-1 && bounds.height>=44*density-1)
        }
        rule.onNodeWithTag("studio-element-pass").performClick();assertEquals(listOf("pass"),calls)
        UiTestSupport.screenshot(rule.activity,"studio-responsive-${width}x$height")
    }
    @Test @Config(qualifiers="w344dp-h800dp-port-xhdpi") fun foldCoverControlsFitAndInvokeNativeAction(){responsiveControlsFit(344,800)}
    @Test @Config(qualifiers="w673dp-h928dp-port-xhdpi") fun openFoldControlsFitAndInvokeNativeAction(){responsiveControlsFit(673,928)}
    @Test @Config(qualifiers="w892dp-h412dp-land-xhdpi") fun landscapeControlsFitAndInvokeNativeAction(){responsiveControlsFit(892,412)}
    @Test fun actualNativeControlsFollowAppliedCoordinatesAndDistinctActions() {
        render()
        val before=rule.onNodeWithTag("studio-element-pass").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {layout.value=layout.value.copy(elements=layout.value.elements.map{it.copy(y=it.y+16f)})}
        rule.waitForIdle()
        val after=rule.onNodeWithTag("studio-element-pass").fetchSemanticsNode().boundsInRoot
        assertTrue(after.top>before.top)
        listOf("pass","undo","actions","metric0","metric2","metric3").forEach {rule.onNodeWithTag("studio-element-$it").performClick()}
        assertEquals(listOf("pass","undo","actions","stack","GRAVEYARD","EXILE"),calls)
        rule.onNodeWithText("YOUR HAND",substring=true).assertDoesNotExist()
        rule.onNodeWithText("Select a card",substring=true).assertDoesNotExist()
    }
    @Test fun busyActionsStayDisabledAndLibraryShowsOnlyOwnCount() {
        busy.value=true;render()
        listOf("pass","undo","actions","cast").forEach {rule.onNodeWithTag("studio-element-$it").assertIsNotEnabled()}
        rule.onNodeWithTag("studio-element-metric1").performClick()
        rule.onNodeWithText("Your library").assertExists()
        rule.onNodeWithText("92 cards.",substring=true).assertExists()
        assertTrue(calls.isEmpty())
    }
    @Test fun selectedCastUsesActualCardIdentityAndEmptyHandHasNoSampleArt() {
        render()
        rule.onNodeWithText("Real card 3").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-element-cast").performClick()
        assertEquals(listOf("select:3","cast:3"),calls)
    }
    @Test fun emptyHandDoesNotRenderExportedSampleCards() {
        render(0)
        rule.onNodeWithText("Nature’s Order").assertDoesNotExist()
        rule.onNodeWithText("Real card",substring=true).assertDoesNotExist()
        rule.onNodeWithTag("studio-element-cast").assertIsNotEnabled()
    }
    @Test fun largeHandExposesOverflowSlotsBelowTheOwnerDesign() {
        rule.setContent {MaterialTheme {
            StudioHandCanvas(StudioLayout.reference(),StudioHandState(emptyMap(),emptySet(),
                (1..24).map{GameCard(it,"Real card $it","Creature",Color.DarkGray)}),{},{},{},modifier=Modifier.width(412.dp))
        }}
        rule.waitForIdle()
        rule.onNodeWithTag("studio-bounds-overflow23").assertExists()
        rule.onNodeWithText("Real card 24").assertExists()
    }
    @Test fun editorDragApplyAndReopenPersistActualCoordinates() {
        val store=StudioStore(rule.activity.getSharedPreferences("studio-ui",0),"portrait")
        store.reset()
        var applied=0
        rule.setContent {MaterialTheme {NativeStudioPage(store,emptyList(),{applied++},{})}}
        rule.waitForIdle()
        rule.onNodeWithTag("studio-element-pass").performScrollTo()
        UiTestSupport.screenshot(rule.activity,"studio-before-drag")
        rule.onNodeWithTag("studio-element-pass").performScrollTo().performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f,8f))
            advanceEventTime(50)
            moveBy(Offset(0f,8f))
            up()
        }
        rule.waitForIdle()
        UiTestSupport.screenshot(rule.activity,"studio-after-drag")
        rule.onNodeWithTag("studio-apply").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1,applied)
        assertNotNull(store.load())
        assertTrue(store.load()!!.elements.first().y>344f)
        assertEquals(store.load(),StudioStore(rule.activity.getSharedPreferences("studio-ui",0),"portrait").load())
        UiTestSupport.screenshot(rule.activity,"studio-native-hand-editor")
    }
    @Test fun editorResizeChangesSavedDimensionsWithoutLosingActionBinding() {
        val store=StudioStore(rule.activity.getSharedPreferences("studio-resize-ui",0),"portrait")
        store.reset()
        rule.setContent {MaterialTheme {NativeStudioPage(store,emptyList(),{},{})}}
        rule.waitForIdle()
        rule.onNodeWithTag("studio-resize-pass").performScrollTo().performTouchInput {
            swipe(center,center+Offset(-48f,0f),300)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("studio-apply").performScrollTo().performClick()
        rule.waitForIdle()
        assertNotNull(store.load())
        assertTrue(store.load()!!.elements.first().w<145f)
        assertEquals("pass",store.load()!!.elements.first().binding)
    }
}

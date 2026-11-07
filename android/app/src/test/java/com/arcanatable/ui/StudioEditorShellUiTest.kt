package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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

/**
 * Drives the redesigned Studio through real touch input on real Studio-wrapped native controls (the same wrappers the
 * gameplay screens use). Robolectric/JVM evidence only: this is not physical-device validation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
class StudioEditorShellUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val calls=mutableListOf<String>()
    private var applied=0
    private lateinit var runtime:StudioControlsRuntime
    private val showShell=mutableStateOf(true)

    private fun render(prefsName:String="studio-shell-${System.nanoTime()}",begin:Boolean=true) {
        runtime=StudioControlsRuntime(rule.activity.getSharedPreferences(prefsName,0),"portrait").also{it.enableCatalog()}
        rule.setContent {MaterialTheme {CompositionLocalProvider(LocalStudioControls provides runtime) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.padding(top=320.dp,start=40.dp)) {
                    StudioScreen("Player") {
                        StudioButton("HomeScreen.OutlinedButton.1","Home",{calls+="play"},Modifier.testTag("play")){StudioText("HomeScreen.Text.1","Home",false,"Play")}
                        Spacer(Modifier.height(24.dp))
                        StudioButton("HomeScreen.OutlinedButton.1","Home",{calls+="decks"},Modifier.testTag("decks")){StudioText("HomeScreen.Text.1","Home",false,"Decks")}
                    }
                }
                if(runtime.editing && showShell.value)StudioEditorShell(runtime,
                    mapOf("Table" to (true to {calls+="nav:Table"}),"Player" to (true to {calls+="nav:Player"}),"Combat" to (false to {calls+="nav:Combat"})),
                    onApplied={applied++},onClassic={calls+="classic"},
                    handCards=(1..5).map{GameCard(it,"Real card $it","Creature",androidx.compose.ui.graphics.Color.DarkGray)})
            }
        }}}
        rule.waitForIdle()
        if(begin){rule.runOnIdle{runtime.begin()};rule.waitForIdle()}
    }
    private fun control(title:String)=runtime.controls.values.single{it.title==title && it.isAction}
    private fun density()=rule.activity.resources.displayMetrics.density
    private fun bounds(tag:String)=rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun tapCanvas(at:Offset)=rule.onNodeWithTag("studio-canvas").performTouchInput{click(at)}
    private fun dragCanvas(from:Offset,by:Offset)=rule.onNodeWithTag("studio-canvas").performTouchInput {
        down(from);repeat(10){moveBy(Offset(by.x/10,by.y/10))};up()
    }

    @Test fun tapOnLiveScreenSelectsWithoutRunningTheGameAction() {
        render()
        tapCanvas(bounds("play").center)
        rule.waitForIdle()
        assertEquals(control("Play").id,runtime.selected)
        assertTrue("edit taps must not reach game actions",calls.isEmpty())
        rule.onNodeWithTag("studio-inspector").assertIsDisplayed()
        tapCanvas(Offset(20f,bounds("play").top-200f));rule.waitForIdle()
        assertNull("tapping empty canvas clears the selection",runtime.selected)
    }

    /** Owner, Nov 7: "I tap it once and can't tap it anymore" (the Width/Height +/- steppers). Every tap must count. */
    @Test fun sizeSteppersKeepWorkingAfterTheFirstTap() {
        render()
        tapCanvas(bounds("play").center);rule.waitForIdle()
        val id=control("Play").id
        val widths=mutableListOf<Float>()
        repeat(3){rule.onNodeWithTag("studio-width-plus").performClick();rule.waitForIdle();widths+=runtime.style(id).width!!}
        assertEquals("three taps, three changes",3,widths.distinct().size)
        val heights=mutableListOf<Float>()
        repeat(3){rule.onNodeWithTag("studio-height-plus").performClick();rule.waitForIdle();heights+=runtime.style(id).height!!}
        assertEquals(3,heights.distinct().size)
        rule.onNodeWithTag("studio-width-minus").performClick();rule.waitForIdle()
        assertEquals(widths[1],runtime.style(id).width!!,0.01f)
    }

    @Test fun handSizeSteppersKeepWorkingAfterTheFirstTap() {
        render()
        openHandScreen()
        rule.onNodeWithTag("studio-hand-canvas-overlay").performTouchInput{click(handPoint("pass"))};rule.waitForIdle()
        val widths=mutableListOf<Float>()
        repeat(3){rule.onNodeWithTag("studio-width-plus").performScrollTo().performClick();rule.waitForIdle();widths+=hand().elements.single{it.id=="pass"}.w}
        assertEquals("three taps, three changes: $widths",3,widths.distinct().size)
        val heights=mutableListOf<Float>()
        repeat(3){rule.onNodeWithTag("studio-height-plus").performScrollTo().performClick();rule.waitForIdle();heights+=hand().elements.single{it.id=="pass"}.h}
        assertEquals("three taps, three changes: $heights",3,heights.distinct().size)
    }

    @Test fun directDragMovesTheRealControlAndIsOneUndoStep() {
        render()
        val before=bounds("play")
        tapCanvas(before.center);rule.waitForIdle()
        dragCanvas(before.center,Offset(40f*density(),24f*density()));rule.waitForIdle()
        val style=runtime.style(control("Play").id)
        assertEquals(40f,style.dx,0.01f);assertEquals(24f,style.dy,0.01f)
        val after=bounds("play")
        assertEquals(before.left+40f*density(),after.left,2f)
        assertTrue(runtime.canUndo)
        rule.onNodeWithTag("studio-undo").performClick();rule.waitForIdle()
        assertEquals(0f,runtime.style(control("Play").id).dx,0.01f);assertFalse(runtime.canUndo)
        rule.onNodeWithTag("studio-redo").performClick();rule.waitForIdle()
        assertEquals(40f,runtime.style(control("Play").id).dx,0.01f)
        assertTrue(calls.isEmpty())
    }

    @Test fun cornerHandleResizesAndLeftHandleKeepsTheRightEdge() {
        render()
        val d=density()
        tapCanvas(bounds("play").center);rule.waitForIdle()
        val before=control("Play").bounds // the frame the handles sit on (includes the 48dp minimum touch area)
        dragCanvas(before.bottomRight,Offset(32f*d,16f*d));rule.waitForIdle()
        val resized=runtime.style(control("Play").id)
        assertEquals((before.width/d+32f).let{Math.round(it/4f)*4f},resized.width!!,0.01f)
        assertEquals((before.height/d+16f).let{Math.round(it/4f)*4f},resized.height!!,0.01f)
        val wide=control("Play").bounds
        assertTrue(wide.width>before.width && wide.height>before.height)
        dragCanvas(Offset(wide.left,wide.center.y),Offset(-20f*d,0f));rule.waitForIdle()
        val left=runtime.style(control("Play").id)
        assertTrue("left handle grows width",left.width!!>resized.width!!)
        assertTrue("left handle shifts the offset left",left.dx<0f)
    }

    @Test fun inspectorNudgeStyleAndColorEditTheSameDraft() {
        render()
        tapCanvas(bounds("play").center);rule.waitForIdle()
        rule.onNodeWithTag("studio-nudge-right").performClick()
        rule.onNodeWithTag("studio-nudge-down").performClick();rule.waitForIdle()
        assertEquals(4f,runtime.style(control("Play").id).dx,0.01f);assertEquals(4f,runtime.style(control("Play").id).dy,0.01f)
        rule.onNodeWithTag("studio-tab-Style").performClick()
        rule.onNodeWithTag("studio-fill-Accent").performClick();rule.waitForIdle()
        assertEquals(tableColorHex(darkTableColors.accent),runtime.style(control("Play").id).fill)
        rule.onNodeWithTag("studio-font-plus").performClick();rule.waitForIdle()
        assertEquals(15f,runtime.style(control("Play").id).font!!,0.01f)
        rule.onNodeWithTag("studio-inspector-body").performTouchInput{swipeUp()};rule.waitForIdle()
        // Real finger scroll of the inspector body (semantics scroll-to is unreliable through nested scrollers).
        rule.onNodeWithTag("studio-icon-Play").assertIsDisplayed().performClick();rule.waitForIdle()
        assertEquals("Play",runtime.style(control("Play").id).icon)
        rule.onNodeWithTag("studio-inspector-body").performTouchInput{swipeDown()};rule.waitForIdle()
        rule.onNodeWithTag("studio-fill-custom").performScrollTo().performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-fill-hex").performScrollTo().performTextReplacement("#123456");rule.waitForIdle()
        assertEquals("#123456",runtime.style(control("Play").id).fill)
    }

    @Test fun assignedActionAndVisibilityApplyToTheRealUi() {
        val prefs="studio-shell-apply"
        render(prefs)
        tapCanvas(bounds("play").center);rule.waitForIdle()
        rule.onNodeWithTag("studio-tab-Action").performClick();rule.waitForIdle()
        rule.onAllNodesWithTag("studio-binding-option").filterToOne(hasText("Decks")).performClick();rule.waitForIdle()
        assertEquals(control("Decks").id,runtime.style(control("Play").id).binding)
        tapCanvas(bounds("decks").center);rule.waitForIdle()
        rule.onNodeWithTag("studio-tab-Arrange").performClick()
        rule.onNodeWithTag("studio-visible").performClick();rule.waitForIdle()
        assertFalse(runtime.style(control("Decks").id).visible)
        rule.onNodeWithTag("studio-apply").performClick();rule.waitForIdle()
        assertFalse(runtime.editing);assertEquals(1,applied)
        rule.onNodeWithTag("studio-editor").assertDoesNotExist()
        rule.onNodeWithTag("decks").assertIsNotDisplayed()
        rule.onNodeWithTag("play").performClick()
        assertEquals("Play now runs the assigned native action",listOf("decks"),calls)
        // Persistence: a fresh runtime reading the same preferences sees the applied project.
        val reloaded=StudioControlsRuntime(rule.activity.getSharedPreferences(prefs,0),"portrait")
        assertEquals(runtime.project(),reloaded.project())
    }

    @Test fun layersPanelSelectsAndTogglesVisibility() {
        render()
        rule.onNodeWithTag("studio-layers").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-layers-panel").assertIsDisplayed()
        rule.onAllNodesWithTag("studio-layer-row").filterToOne(hasText("Decks",substring=true)).performClick();rule.waitForIdle()
        assertEquals(control("Decks").id,runtime.selected)
        rule.onAllNodesWithTag("studio-layer-row").filterToOne(hasText("Play",substring=true)).onChildren().filterToOne(hasTestTag("studio-layer-eye")).performClick()
        rule.waitForIdle()
        assertFalse(runtime.style(control("Play").id).visible)
        rule.onNodeWithTag("studio-layers-close").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-layers-panel").assertDoesNotExist()
    }

    @Test fun screenBarIsAlwaysVisibleAndOnlyOpensAvailableScreens() {
        render()
        rule.onNodeWithTag("studio-screen-bar").assertIsDisplayed()
        listOf("Table","Player","Hand","Combat").forEach{rule.onNodeWithTag("studio-tab-screen-$it").assertExists()}
        rule.onNodeWithTag("studio-tab-screen-Combat").performClick();rule.waitForIdle()
        assertTrue("an unavailable screen explains itself instead of navigating",calls.isEmpty())
        rule.onNodeWithTag("studio-notice").assertIsDisplayed()
        rule.onNodeWithTag("studio-tab-screen-Player").performClick();rule.waitForIdle()
        assertEquals(listOf("nav:Player"),calls)
        rule.onNodeWithTag("studio-screen-title").assert(hasAnyDescendant(hasText("Player")))
        assertTrue("still editing after navigation",runtime.editing)
    }

    @Test fun scrollToolPassesTouchesThroughButStudioControlsStillOnlySelect() {
        render()
        rule.onNodeWithTag("studio-tool-Scroll").performClick();rule.waitForIdle()
        rule.onNodeWithTag("play").performClick();rule.waitForIdle()
        assertTrue(calls.isEmpty())
        assertEquals(control("Play").id,runtime.selected)
    }

    @Test fun closeWithChangesAsksAndKeepsTheDraftWithoutApplying() {
        val prefs="studio-shell-close"
        render(prefs)
        tapCanvas(bounds("play").center);rule.waitForIdle()
        val id=control("Play").id;val nativeLeft=bounds("play").left
        rule.onNodeWithTag("studio-nudge-right").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-close").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-close-confirm").performClick();rule.waitForIdle()
        assertFalse(runtime.editing);assertEquals(0,applied)
        assertEquals("game UI is unchanged until Apply",nativeLeft,bounds("play").left,0.5f)
        rule.runOnIdle{runtime.begin()};rule.waitForIdle()
        assertEquals("draft reopens",4f,runtime.style(id).dx,0.01f)
    }

    @Test fun invalidProjectCannotApplyAndExplainsWhy() {
        render()
        tapCanvas(bounds("play").center);rule.waitForIdle()
        rule.runOnIdle{runtime.edit(control("Play").id,runtime.style(control("Play").id).copy(dx=9000f))};rule.waitForIdle()
        rule.onNodeWithTag("studio-apply").performClick();rule.waitForIdle()
        assertTrue(runtime.editing);assertEquals(0,applied)
        rule.onNodeWithTag("studio-notice").assertIsDisplayed()
    }

    @Test fun selectedControlPushedOffscreenDoesNotStallTheEditor() {
        render()
        rule.runOnIdle{runtime.selected=control("Play").id};rule.waitForIdle()
        rule.runOnIdle{runtime.edit(control("Play").id,runtime.style(control("Play").id).copy(dx=1900f))};rule.waitForIdle()
        rule.onNodeWithTag("studio-inspector").assertIsDisplayed()
        rule.onNodeWithTag("studio-native-position").performScrollTo().performClick();rule.waitForIdle()
        assertEquals(0f,runtime.style(control("Play").id).dx,0f)
    }

    @Test fun optionsExportImportAndProfileWork() {
        render()
        tapCanvas(bounds("play").center);rule.waitForIdle()
        rule.onNodeWithTag("studio-nudge-right").performClick();rule.waitForIdle()
        val exported=runtime.exportProject()
        rule.runOnIdle{runtime.undo()};rule.waitForIdle()
        rule.onNodeWithTag("studio-more").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-more-tab-Project").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-export").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-json-copy").performClick();rule.waitForIdle()
        val clip=rule.activity.getSystemService(android.content.ClipboardManager::class.java)
        assertEquals(runtime.exportProject(),clip.primaryClip!!.getItemAt(0).text.toString())
        clip.setPrimaryClip(android.content.ClipData.newPlainText("bad","not a project"))
        rule.onNodeWithTag("studio-json-import").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-json-status").assertExists()
        clip.setPrimaryClip(android.content.ClipData.newPlainText("project",exported))
        rule.onNodeWithTag("studio-json-import").performClick();rule.waitForIdle()
        assertEquals(4f,runtime.style(control("Play").id).dx,0.01f)
        rule.onNodeWithTag("studio-more-tab-Layout").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-density-Minimal").assertIsDisplayed().performClick();rule.waitForIdle()
        assertEquals("Minimal",runtime.draft.screenDensity["Player"])
        rule.onNodeWithTag("studio-profile-name").performScrollTo().performTextReplacement("Fold cover")
        rule.onNodeWithTag("studio-profile-copy").performClick();rule.waitForIdle()
        assertEquals("Fold cover",runtime.profile)
        rule.onNodeWithTag("studio-more-tab-Project").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-classic").performClick();rule.waitForIdle()
        assertEquals(listOf("classic"),calls)
    }

    // ---- Hand screen: same editor, absolute hand layout, shared draft/undo/Apply ----
    private fun prefs()=runtime.prefs
    private fun hand()=rule.activity.resources.configuration.let{StudioHandEditing.layout(runtime,it.screenWidthDp.toFloat(),it.screenHeightDp.toFloat())}
    private fun openHandScreen() {
        rule.onNodeWithTag("studio-tab-screen-Hand").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-hand-workspace").assertIsDisplayed()
        rule.onNodeWithTag("studio-hand-drawer").assertIsDisplayed()
        rule.onNodeWithTag("studio-screen-title").assert(hasAnyDescendant(hasText("Hand")))
    }
    /** Root-space point of a hand element → local point on the hand overlay (what a finger would touch). */
    private fun handPoint(id:String,at:(androidx.compose.ui.geometry.Rect)->Offset={it.center}):Offset {
        val overlay=bounds("studio-hand-canvas-overlay");return at(bounds("studio-bounds-$id"))-overlay.topLeft
    }
    private fun ownerHand()=rule.activity.assets.open("studio/owner-hand.json").bufferedReader().use{StudioLayout.parse(it.readText())}

    @Test fun handScreenSelectsMovesResizesWithTheSharedUndo() {
        render()
        openHandScreen()
        rule.onNodeWithTag("studio-hand-canvas-overlay").performTouchInput{click(handPoint("undo"))};rule.waitForIdle()
        rule.onNodeWithTag("studio-inspector").assertIsDisplayed().assert(hasAnyDescendant(hasText("UNDO",substring=true)))
        assertTrue("selecting in the editor never runs the hand action",calls.isEmpty())
        val before=hand().elements.single{it.id=="undo"}
        val unit=bounds("studio-bounds-undo").width/before.w
        rule.onNodeWithTag("studio-hand-canvas-overlay").performTouchInput{
            down(handPoint("undo"));repeat(8){moveBy(Offset(0f,40f*unit/8))};up()}
        rule.waitForIdle()
        val moved=hand().elements.single{it.id=="undo"}
        assertEquals("travel snapped to the hand's 8-unit grid",before.y+40f,moved.y,0.01f);assertEquals(before.x,moved.x,0.01f)
        rule.onNodeWithTag("studio-hand-canvas-overlay").performTouchInput{
            down(handPoint("undo"){it.bottomRight});repeat(8){moveBy(Offset(24f*unit/8,16f*unit/8))};up()}
        rule.waitForIdle()
        val resized=hand().elements.single{it.id=="undo"}
        assertTrue(resized.w>moved.w && resized.h>moved.h)
        rule.onNodeWithTag("studio-undo").performClick();rule.waitForIdle()
        assertEquals(moved,hand().elements.single{it.id=="undo"})
        rule.onNodeWithTag("studio-nudge-right").performClick();rule.waitForIdle()
        assertEquals(moved.x+8f,hand().elements.single{it.id=="undo"}.x,0.01f)
        rule.onNodeWithTag("studio-tab-Action").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-hand-binding-metric0").performScrollTo().performClick();rule.waitForIdle()
        assertEquals("metric0",hand().elements.single{it.id=="undo"}.binding)
        // Owner note 14 (Nov 6) changed this contract: Undo may be left unreachable. No warning bar, and Apply saves.
        rule.onNodeWithTag("studio-notice").assertDoesNotExist()
        rule.onNodeWithTag("studio-apply").performClick();rule.waitForIdle()
        assertFalse(runtime.editing);assertEquals(1,applied)
    }

    @Test fun ownerHandJsonLoadsAndAppliesExactly() {
        render("studio-owner-hand")
        openHandScreen()
        rule.onNodeWithTag("studio-more").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-more-tab-Hand").assertIsDisplayed()
        rule.onNodeWithTag("studio-hand-owner").performClick();rule.waitForIdle()
        val owner=ownerHand()
        assertEquals(owner,hand());assertEquals(owner.json(),hand().json())
        rule.onNodeWithTag("studio-more-panel-close").performClick();rule.waitForIdle()
        UiTestSupport.screenshot(rule.activity,"studio-hand-owner-design")
        rule.onNodeWithTag("studio-apply").performClick();rule.waitForIdle()
        assertFalse(runtime.editing);assertEquals(1,applied)
        val store=StudioStore(prefs(),"portrait",runtime.profile)
        assertEquals("the gameplay hand drawer reads exactly the owner's design",owner,store.load());assertTrue(store.enabled())
    }

    @Test fun bareHandJsonImportsFromClipboardUnchanged() {
        render()
        openHandScreen()
        val owner=ownerHand()
        rule.activity.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("hand",rule.activity.assets.open("studio/owner-hand.json").bufferedReader().use{it.readText()}))
        rule.onNodeWithTag("studio-more").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-more-tab-Project").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-export").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-json-import").performClick();rule.waitForIdle()
        assertEquals(owner,hand())
    }

    @Test fun handLayersAndControlEditsShareOneDraftAndOneApply() {
        val name="studio-shared-apply"
        render(name)
        tapCanvas(bounds("play").center);rule.waitForIdle()
        rule.onNodeWithTag("studio-nudge-right").performClick();rule.waitForIdle()
        openHandScreen()
        rule.onNodeWithTag("studio-layers").performClick();rule.waitForIdle()
        rule.onAllNodesWithTag("studio-layer-row").filterToOne(hasText("CAST",substring=true)).performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-layers-close").performClick();rule.waitForIdle()
        rule.onNodeWithTag("studio-tab-Style").performClick()
        rule.onNodeWithTag("studio-fill-Gold").performClick();rule.waitForIdle()
        assertEquals(tableColorHex(darkTableColors.counter),hand().elements.single{it.id=="cast"}.color)
        assertTrue("hand canvas cards are not registered as live controls",runtime.problems().isEmpty())
        rule.onNodeWithTag("studio-apply").performClick();rule.waitForIdle()
        assertFalse(runtime.editing)
        assertEquals(4f,StudioControlStore(prefs(),"portrait",runtime.profile).load().controls.values.single().dx,0.01f)
        assertEquals(tableColorHex(darkTableColors.counter),StudioStore(prefs(),"portrait",runtime.profile).load()!!.elements.single{it.id=="cast"}.color)
    }

    @Test @Config(qualifiers="w344dp-h800dp-port-xhdpi") fun foldCoverHandScreen() {
        render()
        openHandScreen()
        rule.onNodeWithTag("studio-hand-canvas-overlay").performTouchInput{click(handPoint("pass"))};rule.waitForIdle()
        rule.onNodeWithTag("studio-inspector").assertIsDisplayed()
        UiTestSupport.screenshot(rule.activity,"studio-hand-fold-cover-344x800")
    }

    private fun barFits(width:Int,name:String) {
        render()
        tapCanvas(bounds("play").center);rule.waitForIdle()
        val d=density()
        listOf("studio-close","studio-undo","studio-redo","studio-layers","studio-more","studio-apply").forEach {tag->
            val b=bounds(tag)
            assertTrue("$tag inside ${width}dp: $b",b.left>=-1f && b.right<=width*d+1f)
            assertTrue("$tag is a 44dp target: $b",b.width>=44f*d-1f && b.height>=44f*d-1f)
        }
        rule.onNodeWithTag("studio-inspector").assertIsDisplayed()
        UiTestSupport.screenshot(rule.activity,"studio-editor-$name")
    }
    @Test @Config(qualifiers="w344dp-h800dp-port-xhdpi") fun foldCoverToolbarFitsWithTouchTargets(){barFits(344,"fold-cover-344x800")}
    @Test @Config(qualifiers="w673dp-h928dp-port-xhdpi") fun unfoldedToolbarFitsWithTouchTargets(){barFits(673,"fold-open-673x928")}
}

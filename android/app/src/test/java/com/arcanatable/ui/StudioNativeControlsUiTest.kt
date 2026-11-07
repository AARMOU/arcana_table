package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class StudioNativeControlsUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    /** Owner, Nov 7: "the text/image goes outside the button". A resized button's contents shrink to fit inside it. */
    @Test fun resizedButtonKeepsItsTextInsideItsEdges() {
        val runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("controls-fit",0),"portrait").also{it.enableCatalog()}
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime){Column {
            StudioButton("HomeScreen.OutlinedButton.1","Player",{},Modifier.testTag("b")){
                StudioText("HomeScreen.Text.1","Player",false,"PASS TO ATTACKERS",modifier=Modifier.testTag("t"))}
        }}}}
        rule.waitForIdle()
        val id=runtime.controls.values.single{it.isAction}.id
        rule.runOnIdle{runtime.begin(id);runtime.edit(id,StudioControlStyle(width=72f,height=31f));runtime.apply()}
        rule.waitForIdle()
        val b=rule.onNodeWithTag("b").fetchSemanticsNode().boundsInRoot
        assertEquals(72f*rule.density.density,b.width,2f);assertEquals(31f*rule.density.density,b.height,2f)
        rule.onNodeWithTag("t",useUnmergedTree=true).assertExists()
    }

    @Test fun actualButtonMovesAndRebindsOnlyToAnAvailableNativeCallback() {
        val runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("controls-ui",0),"portrait").also{it.enableCatalog()}
        val enabled=mutableStateOf(true);val calls=mutableListOf<String>()
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime){Column {
            StudioButton("HomeScreen.OutlinedButton.1","Player",{calls+="source"},Modifier.testTag("source")){StudioText("HomeScreen.Text.1","Player",false,"Source")}
            StudioButton("HomeScreen.OutlinedButton.1","Player",{calls+="target"},Modifier.testTag("target"),enabled=enabled.value){StudioText("HomeScreen.Text.1","Player",false,"Target")}
        }}}}
        rule.waitForIdle()
        val source=runtime.controls.values.single{it.title=="Source"}
        val target=runtime.controls.values.single{it.title=="Target"}
        val before=rule.onNodeWithTag("source").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle{runtime.begin(source.id);runtime.edit(source.id,StudioControlStyle(dx=8f,binding=target.id));runtime.apply()}
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag("source").fetchSemanticsNode().boundsInRoot.left>before.left)
        rule.onNodeWithTag("source").performClick();assertEquals(listOf("target"),calls)
        rule.runOnIdle{enabled.value=false};rule.waitForIdle()
        rule.onNodeWithTag("source").assertIsNotEnabled()
    }
    @Test fun inspectModeSelectsNativeControlsWithoutPerformingGameActions() {
        val runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("controls-inspect",0),"portrait")
        var actions=0
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime){
            StudioButton("HomeScreen.OutlinedButton.1","Player",{actions++},Modifier.testTag("action")){StudioText("HomeScreen.Text.1","Player",false,"Play")}
        }}}
        rule.waitForIdle();rule.runOnIdle{runtime.begin();runtime.inspectNativeScreen()};rule.waitForIdle()
        rule.onNodeWithTag("action").performClick();assertEquals(0,actions);assertNotNull(runtime.selected);assertTrue(runtime.panelOpen)
        rule.runOnIdle{runtime.cancel()};rule.waitForIdle();rule.onNodeWithTag("action").performClick();assertEquals(1,actions)
    }
    @Test fun liveInformationCanBeHiddenWithoutSerializingItsCurrentValue() {
        val runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("controls-data",0),"portrait").also{it.enableCatalog()}
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime){
            StudioText("HomeScreen.Text.1","Player",true,"Live life 40",Modifier.testTag("life"))
        }}}
        rule.waitForIdle();val id=runtime.controls.values.single().id
        rule.runOnIdle{runtime.begin(id);runtime.edit(id,StudioControlStyle(visible=false));runtime.apply()};rule.waitForIdle()
        rule.onNodeWithTag("life").assertDoesNotExist()
        assertFalse(runtime.store().load().json().contains("Live life 40"))
    }
    @Test fun informationModuleStaysLiveAcrossBindingReloadAndHiddenOriginal() {
        val prefs=rule.activity.getSharedPreferences("controls-live-module",0)
        val state=mutableStateOf(StudioControlsRuntime(prefs,"portrait").also{it.enableCatalog()})
        val life=mutableStateOf(40);val mana=mutableStateOf(3)
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides state.value){Column(Modifier.fillMaxSize()){
            StudioText("HomeScreen.Text.1","Player",true,"Life ${life.value}",Modifier.testTag("life"))
            StudioText("HomeScreen.Text.2","Player",true,androidx.compose.ui.text.AnnotatedString("Mana ${mana.value}"),Modifier.testTag("mana"))
        }}}}
        rule.waitForIdle();var runtime=state.value
        val source=runtime.controls.values.single{it.title=="Life 40"}.id
        val target=runtime.controls.values.single{it.title=="Mana 3"}.id
        rule.runOnIdle{runtime.begin(source);runtime.duplicate(source,rule.density.density)}
        rule.waitForIdle();val copy=runtime.draft.copies.single()
        rule.runOnIdle{runtime.edit(copy.id,copy.style.copy(dx=8f,dy=180f,width=160f,height=48f));runtime.apply()}
        rule.waitForIdle();rule.onNodeWithTag("studio-copy-${copy.id}").assertTextEquals("Life 40")
        rule.runOnIdle{life.value=39};rule.waitForIdle()
        rule.onNodeWithTag("studio-copy-${copy.id}").assertTextEquals("Life 39")
        assertFalse(runtime.store().load().json().contains("Life 39"))
        rule.runOnIdle{state.value=StudioControlsRuntime(prefs,"portrait")};rule.waitForIdle();runtime=state.value
        rule.onNodeWithTag("studio-copy-${copy.id}").assertTextEquals("Life 39")
        rule.runOnIdle{runtime.begin(copy.id);runtime.edit(copy.id,runtime.style(copy.id).copy(binding=target));runtime.edit(source,StudioControlStyle(visible=false));runtime.apply()}
        rule.waitForIdle();rule.onNodeWithTag("life").assertDoesNotExist()
        rule.onNodeWithTag("studio-copy-${copy.id}").assertTextEquals("Mana 3")
        rule.runOnIdle{mana.value=2};rule.waitForIdle()
        rule.onNodeWithTag("studio-copy-${copy.id}").assertTextEquals("Mana 2")
        assertFalse(runtime.invoke(copy.id))
        assertFalse(runtime.store().load().json().contains("Mana 2"))
        rule.runOnIdle{runtime.begin(target);runtime.duplicate(target,rule.density.density)}
        rule.waitForIdle();val richCopy=runtime.draft.copies.single{it.id!=copy.id}
        rule.runOnIdle{runtime.edit(richCopy.id,richCopy.style.copy(dx=8f,dy=260f,width=160f,height=48f));runtime.edit(target,StudioControlStyle(visible=false));runtime.apply()}
        rule.waitForIdle();rule.onNodeWithTag("mana").assertDoesNotExist()
        rule.onNodeWithTag("studio-copy-${richCopy.id}").assertTextEquals("Mana 2")
        rule.runOnIdle{mana.value=1};rule.waitForIdle()
        rule.onNodeWithTag("studio-copy-${richCopy.id}").assertTextEquals("Mana 1")
        rule.onNodeWithTag("studio-copy-${copy.id}").assertTextEquals("Mana 1")

    }
    /**
     * Owner D33 removed the floating in-window Studio button. The guarantee it gave is now enforced at Apply instead:
     * a non-dismissable window can never lose its last visible button, so it can't become a dead end.
     */
    @Test fun nondismissableCustomizedDialogCanNeverLoseItsLastButton() {
        val runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("controls-window-recovery",0),"portrait").also{it.enableCatalog()}
        var gameActions=0;var dismissed=0;val alert=mutableStateOf(false)
        @Composable fun Choice() {StudioButton("HomeScreen.OutlinedButton.1","Choice",{gameActions++},Modifier.testTag("only-choice")){
            StudioText("HomeScreen.Text.1","Choice",false,"Only choice")
        }}
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime){
            val properties=androidx.compose.ui.window.DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)
            if(alert.value)StudioAlertDialog(onDismissRequest={dismissed++},confirmButton={Choice()},properties=properties)
            else StudioDialog(onDismissRequest={dismissed++},properties=properties) {androidx.compose.material3.Surface{Choice()}}
        }}}
        listOf(false,true).forEach {useAlert->
            rule.runOnIdle{alert.value=useAlert};rule.waitForIdle()
            val source=runtime.controls.values.single{it.isAction}.id
            rule.runOnIdle{runtime.begin(source);runtime.edit(source,StudioControlStyle(visible=false))}
            assertTrue(runtime.problems().any{it.startsWith("Keep at least one button visible")})
            assertTrue("Apply refuses the dead-end window",runCatching{runtime.apply()}.isFailure)
            rule.runOnIdle{runtime.cancel();runtime.enableCatalog()};rule.waitForIdle()
            rule.onNodeWithTag("studio-window-escape").assertDoesNotExist()
            rule.onNodeWithTag("only-choice").assertIsDisplayed()
            assertEquals(0,gameActions);assertEquals(0,dismissed)
        }
    }
    @Test fun repeatedListControlsHaveDistinctCallbacksAndStableSlots() {
        val runtime=StudioControlsRuntime(rule.activity.getSharedPreferences("controls-repeat",0),"portrait").also{it.enableCatalog()}
        val calls=mutableListOf<Int>()
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides runtime){Column {
            (0..9).forEach {i->StudioButton("HomeScreen.OutlinedButton.1","Player",{calls+=i},Modifier.testTag("repeat-$i")){
                StudioText("HomeScreen.Text.1","Player",true,"Choice $i")
            }}
        }}}}
        rule.waitForIdle()
        assertEquals(10,runtime.controls.values.count{it.isAction})
        rule.onNodeWithTag("repeat-5").performClick();rule.onNodeWithTag("repeat-0").performClick()
        assertEquals(listOf(5,0),calls)
    }
    @Test fun duplicateUsesCurrentCallbackSurvivesRuntimeReloadAndRecoversHiddenOriginal() {
        val prefs=rule.activity.getSharedPreferences("controls-copy",0)
        val state=mutableStateOf(StudioControlsRuntime(prefs,"portrait").also{it.enableCatalog()});var calls=0
        rule.setContent{MaterialTheme{CompositionLocalProvider(LocalStudioControls provides state.value){Column(Modifier.fillMaxSize()){
            StudioButton("HomeScreen.OutlinedButton.1","Player",{calls++},Modifier.testTag("source")){StudioText("HomeScreen.Text.1","Player",false,"Source")}
        }}}}
        rule.waitForIdle();var runtime=state.value
        val source=runtime.controls.values.single{it.isAction}.id
        rule.runOnIdle{runtime.begin(source);runtime.duplicate(source,rule.density.density)}
        rule.waitForIdle();val copy=runtime.draft.copies.single()
        rule.runOnIdle{runtime.edit(copy.id,copy.style.copy(dx=8f,dy=180f,width=120f,height=48f))}
        rule.waitForIdle();rule.runOnIdle{runtime.apply()};rule.waitForIdle()
        rule.onNodeWithTag("studio-copy-${copy.id}").performClick();assertEquals(1,calls)
        rule.runOnIdle{state.value=StudioControlsRuntime(prefs,"portrait")};rule.waitForIdle();runtime=state.value
        assertTrue(runtime.controls.containsKey(source))
        rule.onNodeWithTag("studio-copy-${copy.id}").performClick();assertEquals(2,calls)
        rule.runOnIdle{runtime.begin(source);runtime.edit(source,StudioControlStyle(visible=false));runtime.apply()};rule.waitForIdle()
        rule.onNodeWithTag("source").assertIsNotDisplayed()
        rule.onNodeWithTag("studio-copy-${copy.id}").performClick();assertEquals(3,calls)
        rule.runOnIdle{runtime.recover(copy.id,"Test viewport recovery")};rule.waitForIdle()
        rule.onNodeWithTag("source").assertIsDisplayed()
    }
}

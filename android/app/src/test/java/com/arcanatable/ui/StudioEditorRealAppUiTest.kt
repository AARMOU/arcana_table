package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The redesigned editor inside the real MainActivity: opened from the real escape control, editing real Home controls,
 * navigating to a real screen, and Apply changing the real UI after the runtime is rebuilt. Robolectric, not a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
class StudioEditorRealAppUiTest {
    @get:Rule val rule=createEmptyComposeRule()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun prefs()=context.getSharedPreferences("arcana_options",Context.MODE_PRIVATE)

    @Test fun realAppEditMoveNavigateAndApply() {
        StudioProfiles(prefs(),"portrait").resetAll()
        context.getSharedPreferences("arcana_tutorial",Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2",setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            UiTestSupport.settle(rule)
            if(rule.onAllNodesWithText("NOT NOW").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("NOT NOW").performClick();UiTestSupport.settle(rule)}
            scenario.onActivity {it.tableController!!.studioControlsRuntime!!.enableCatalog()}
            UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-root-escape").assertIsDisplayed().performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-editor").assertIsDisplayed()
            rule.onNodeWithTag("studio-top-bar").assertIsDisplayed()
            var id="";var height=0f;var density=1f;var target=Offset.Zero;var before=0f
            scenario.onActivity {activity->
                val runtime=activity.tableController!!.studioControlsRuntime!!
                assertTrue(runtime.editing)
                density=activity.resources.displayMetrics.density
                height=activity.window.decorView.height.toFloat()
                val window=System.identityHashCode(activity.window.decorView.rootView)
                // The real Home PLAY button (the table board behind Home must not be what gets picked).
                val c=runtime.controls.values.single{it.isAction && it.window==window && it.screen=="Home" && it.title.equals("PLAY",true)}
                id=c.id;target=c.bounds.center;before=c.bounds.top
                UiTestSupport.screenshot(activity,"studio-real-home-editor")
            }
            rule.onNodeWithTag("studio-canvas").performTouchInput{click(target)};UiTestSupport.settle(rule)
            scenario.onActivity {activity->
                val c=activity.tableController!!
                assertEquals("tap selects the real control",id,c.studioControlsRuntime!!.selected)
                assertFalse("no navigation happened from an edit tap",c.optionsOpen || c.deckPickerOpen)
            }
            rule.onNodeWithTag("studio-inspector").assertIsDisplayed()
            rule.onNodeWithTag("studio-canvas").performTouchInput{down(target);repeat(8){moveBy(Offset(0f,3f*density))};up()}
            UiTestSupport.settle(rule)
            scenario.onActivity {activity->
                assertEquals(24f,activity.tableController!!.studioControlsRuntime!!.style(id).dy,0.01f)
                UiTestSupport.screenshot(activity,"studio-real-home-moved")
            }
            // Validation is the runtime's: an overlapping move is refused at Apply with a visible reason, not silently saved.
            val blocked=scenario.let{var p=listOf<String>();it.onActivity{a->p=a.tableController!!.studioControlsRuntime!!.problems()};p}
            if(blocked.isNotEmpty()) {
                rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
                rule.onNodeWithTag("studio-notice").assertIsDisplayed()
                scenario.onActivity {assertTrue(it.tableController!!.studioControlsRuntime!!.editing)}
                rule.onNodeWithTag("studio-undo").performClick();UiTestSupport.settle(rule)
            }
            rule.onNodeWithTag("studio-tab-Style").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-fill-Gold").assertIsDisplayed().performClick();UiTestSupport.settle(rule)
            // A game screen that is its own window (Settings dialog): the toolbar floats above it and dialog controls edit in place.
            rule.onNodeWithTag("studio-screen-picker").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-screen-Settings").performClick();UiTestSupport.settle(rule)
            scenario.onActivity {activity->
                val c=activity.tableController!!
                assertTrue(c.optionsOpen);assertTrue(c.studioControlsRuntime!!.editing)
            }
            rule.onNodeWithTag("studio-floating-bar").assertIsDisplayed()
            val dialogToggle=scenario.let{var t="";it.onActivity{a->val r=a.tableController!!.studioControlsRuntime!!
                val main=System.identityHashCode(a.window.decorView.rootView)
                t=r.controls.values.first{it.window!=main && it.kind=="toggle" && it.bounds.width>0f}.id};t}
            scenario.onActivity {it.tableController!!.studioControlsRuntime!!.selected=dialogToggle}
            UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-floating-inspector").assertIsDisplayed()
            scenario.onActivity {UiTestSupport.screenshot(it,"studio-real-settings-dialog-editor")}
            // While the editor is open the dialog's own Studio button is replaced by the floating toolbar above it.
            rule.onAllNodesWithTag("studio-window-escape").assertCountEquals(0)
            rule.onNode(hasTestTag("studio-screen-picker") and hasAnyAncestor(hasTestTag("studio-floating-bar"))).performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-overlay-window").assertIsDisplayed()
            rule.onNodeWithTag("studio-screen-Home").performClick();UiTestSupport.settle(rule)
            scenario.onActivity {activity->assertFalse(activity.tableController!!.optionsOpen)}
            rule.onNodeWithTag("studio-floating-bar").assertDoesNotExist()
            rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
            scenario.onActivity {activity->
                val runtime=activity.tableController!!.studioControlsRuntime!!
                assertFalse(runtime.editing)
                assertEquals("applied project persisted",tableColorHex(darkTableColors.counter),StudioControlStore(prefs(),"portrait",runtime.profile).load().controls[id]!!.fill)
                assertEquals(tableColorHex(darkTableColors.counter),runtime.style(id).fill)
                assertEquals(24f,runtime.style(id).dy,0.01f)
                UiTestSupport.screenshot(activity,"studio-real-home-applied")
            }
            UiTestSupport.settle(rule)
            scenario.onActivity {activity->
                val runtime=activity.tableController!!.studioControlsRuntime!!
                assertTrue("the real PLAY button renders at its applied position",runtime.controls[id]!!.bounds.top>before+20f*density)
            }
        } finally {scenario.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }

    /** The existing "Arcana Studio" hand entry point now opens the hand screen of the new editor, not the old page. */
    @Test fun handEntryPointOpensTheNewEditorAndAppliesTheOwnerDesign() {
        StudioProfiles(prefs(),"portrait").resetAll()
        context.getSharedPreferences("arcana_tutorial",Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2",setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            UiTestSupport.settle(rule)
            if(rule.onAllNodesWithText("NOT NOW").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("NOT NOW").performClick();UiTestSupport.settle(rule)}
            scenario.onActivity {it.tableController!!.nativeStudioOpen=true}
            UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-workspace").assertIsDisplayed()
            rule.onAllNodesWithText("Native Argentum hand layout",substring=true).assertCountEquals(0)
            scenario.onActivity {activity->
                val c=activity.tableController!!
                assertFalse(c.nativeStudioOpen);assertTrue(c.studioControlsRuntime!!.editing)
                UiTestSupport.screenshot(activity,"studio-real-hand-editor")
            }
            rule.onNodeWithTag("studio-more").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-owner").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-more-panel-close").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
            val owner=context.assets.open("studio/owner-hand.json").bufferedReader().use{StudioLayout.parse(it.readText())}
            scenario.onActivity {activity->
                assertFalse(activity.tableController!!.studioControlsRuntime!!.editing)
                val store=StudioProfiles(prefs(),"portrait").activeStore()
                assertTrue(store.enabled());assertEquals("gameplay hand drawer source is the owner's design, unchanged",owner,store.load())
            }
        } finally {scenario.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }

    /** A project that hides the main action and paints everything black still leaves the Studio route working. */
    @Test fun allBlackHiddenProjectStillHasAWayBackAndResets() {
        StudioProfiles(prefs(),"portrait").resetAll()
        context.getSharedPreferences("arcana_tutorial",Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2",setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            UiTestSupport.settle(rule)
            if(rule.onAllNodesWithText("NOT NOW").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("NOT NOW").performClick();UiTestSupport.settle(rule)}
            scenario.onActivity {it.tableController!!.studioControlsRuntime!!.enableCatalog()}
            UiTestSupport.settle(rule)
            var id=""
            scenario.onActivity {activity->
                val r=activity.tableController!!.studioControlsRuntime!!
                val window=System.identityHashCode(activity.window.decorView.rootView)
                id=r.controls.values.first{it.screen=="Home" && it.isAction && it.window==window && it.title.equals("PLAY",true)}.id
                r.begin();r.edit(id,r.style(id).copy(visible=false))
                r.change(r.draft.copy(theme=StudioLayout.themeRoles.associateWith{"#000000"}))
            }
            UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
            scenario.recreate();UiTestSupport.settle(rule)
            // The escape lives outside the editable project, so it survives black palettes and hidden controls.
            rule.onNodeWithTag("studio-root-escape").assertIsDisplayed().performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-editor").assertIsDisplayed()
            scenario.onActivity {UiTestSupport.screenshot(it,"studio-real-recovery-all-black")}
            rule.onNodeWithTag("studio-more").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-more-tab-Project").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-reset-profile").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-confirm").performClick();UiTestSupport.settle(rule)
            scenario.onActivity {activity->
                val r=activity.tableController!!.studioControlsRuntime!!
                assertTrue(r.project().controls.isEmpty());assertTrue(r.project().theme.isEmpty())
            }
        } finally {scenario.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }

    /** Landscape uses its own orientation project; the toolbar and hand screen work there too. */
    @Test @Config(qualifiers="w892dp-h412dp-land-xhdpi") fun landscapeEditorAndHandScreen() {
        StudioProfiles(prefs(),"landscape").resetAll()
        context.getSharedPreferences("arcana_tutorial",Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2",setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            UiTestSupport.settle(rule)
            if(rule.onAllNodesWithText("NOT NOW").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("NOT NOW").performClick();UiTestSupport.settle(rule)}
            scenario.onActivity {it.tableController!!.nativeStudioOpen=true}
            UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-workspace").assertIsDisplayed()
            rule.onNodeWithTag("studio-apply").assertIsDisplayed()
            scenario.onActivity {activity->
                assertEquals("landscape",activity.tableController!!.studioControlsRuntime!!.orientation)
                UiTestSupport.screenshot(activity,"studio-real-landscape-hand")
            }
            rule.onNodeWithTag("studio-more").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-Balanced").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-more-panel-close").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
            assertNotNull("landscape hand saved separately",StudioStore(prefs(),"landscape",StudioProfiles(prefs(),"landscape").activeName()).load())
            assertNull("portrait untouched",StudioStore(prefs(),"portrait","Default").load())
        } finally {scenario.close();StudioProfiles(prefs(),"landscape").resetAll()}
    }
}

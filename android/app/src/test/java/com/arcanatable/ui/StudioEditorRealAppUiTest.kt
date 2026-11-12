package com.arcanatable.ui

import android.content.Context
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
 * Studio in the real MainActivity outside a game (owner D33): entry points, no floating button, excluded screens,
 * the practice offer and the hand screen. In-game editing is covered by [StudioCoverageAuditTest]. Robolectric only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
class StudioEditorRealAppUiTest {
    @get:Rule val rule=createEmptyComposeRule()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun prefs()=context.getSharedPreferences("arcana_options",Context.MODE_PRIVATE)
    private fun runtime(s:ActivityScenario<MainActivity>):StudioControlsRuntime{var r:StudioControlsRuntime?=null;s.onActivity{r=it.tableController!!.studioControlsRuntime};return r!!}
    private fun launch(orientation:String="portrait"):ActivityScenario<MainActivity> {
        StudioProfiles(prefs(),orientation).resetAll()
        context.getSharedPreferences("arcana_tutorial",Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2",setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        val s=ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        if(rule.onAllNodesWithText("NOT NOW").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("NOT NOW").performClick();UiTestSupport.settle(rule)}
        return s
    }

    @Test fun homeOpensStudioWhichOffersPracticeAndTheHand() {
        val s=launch()
        try {
            rule.onAllNodesWithTag("studio-root-escape").assertCountEquals(0)
            rule.onNodeWithTag("home-open-studio").assertIsDisplayed().performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-editor").assertIsDisplayed()
            rule.onNodeWithTag("studio-screen-bar").assertIsDisplayed()
            rule.onNodeWithTag("studio-practice-card").assertIsDisplayed()
            rule.onNodeWithTag("studio-start-practice").assertIsDisplayed()
            s.onActivity{UiTestSupport.screenshot(it,"studio-b1-home-practice")}
            rule.onNodeWithTag("studio-practice-hand").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-drawer").assertIsDisplayed()
            // The hand drawer sits below the toolbar and screen bar, never under them.
            val bar=rule.onNodeWithTag("studio-screen-bar").fetchSemanticsNode().boundsInRoot
            assertTrue(rule.onNodeWithTag("studio-hand-drawer").fetchSemanticsNode().boundsInRoot.top>=bar.bottom)
            s.onActivity{UiTestSupport.screenshot(it,"studio-b1-hand-half-drawer")}
        } finally {s.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }

    @Test fun settingsReplacesAdjustUiAndIsNeverEditedOrRecoloured() {
        val s=launch()
        try {
            s.onActivity{it.tableController!!.optionsOpen=true};UiTestSupport.settle(rule)
            rule.onAllNodesWithText("ADJUST UI LAYOUT",substring=true).assertCountEquals(0)
            rule.onNodeWithTag("settings-open-studio").performScrollTo().assertIsDisplayed()
            // Excluded screens never register controls, even while Studio is cataloguing.
            s.onActivity{it.tableController!!.studioControlsRuntime!!.enableCatalog()};UiTestSupport.settle(rule)
            val r=runtime(s)
            assertTrue("Settings controls are not editable",r.controls.values.none{it.screen=="Settings" || it.screen.startsWith("Table Dialogs")})
            assertTrue("Home controls are not editable",r.controls.values.none{it.screen=="Home"})
            rule.onAllNodesWithTag("studio-window-escape").assertCountEquals(0)
            // An applied all-black palette never reaches Settings, and Settings still opens Studio.
            s.onActivity{a->val rt=a.tableController!!.studioControlsRuntime!!
                rt.begin();rt.change(rt.draft.copy(theme=StudioLayout.themeRoles.associateWith{"#000000"}));rt.apply()}
            UiTestSupport.settle(rule)
            s.onActivity{it.tableController!!.optionsOpen=true};UiTestSupport.settle(rule)
            s.onActivity{UiTestSupport.screenshot(it,"studio-b1-settings-unaffected")}
            rule.onNodeWithText("Follow the active player",substring=true).assertIsDisplayed()
            rule.onNodeWithTag("settings-open-studio").performScrollTo().performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-editor").assertIsDisplayed()
            s.onActivity{assertFalse("opening Studio closes Settings",it.tableController!!.optionsOpen)}
        } finally {s.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }

    @Test fun editsLeftOnExcludedScreensAreDroppedWhenStudioOpens() {
        val s=launch()
        try {
            val store=StudioControlStore(prefs(),"portrait","Default")
            val homeId="native:HomeScreen.OutlinedButton.1/1:0"
            store.apply(StudioControlProject(controls=mapOf(homeId to StudioControlStyle(fill="#FF0000")),owners=mapOf(homeId to "Home"),
                screenDensity=mapOf("Settings" to "Minimal","Table" to "Detailed")))
            rule.onNodeWithTag("home-open-studio").performClick();UiTestSupport.settle(rule)
            val r=runtime(s)
            assertFalse(r.draft.controls.containsKey(homeId))
            assertNull(r.draft.screenDensity["Settings"]);assertEquals("Detailed",r.draft.screenDensity["Table"])
        } finally {s.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }

    /** Landscape keeps its own orientation project; the hand screen works there too. */
    @Test @Config(qualifiers="w892dp-h412dp-land-xhdpi") fun landscapeHandScreen() {
        val s=launch("landscape")
        try {
            s.onActivity{it.tableController!!.nativeStudioOpen=true};UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-tab-screen-Hand").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-workspace").assertIsDisplayed()
            // Owner feedback bug 24 changed this contract: one layout for every orientation and fold state.
            s.onActivity{a->assertEquals(STUDIO_LAYOUT_KEY,a.tableController!!.studioControlsRuntime!!.orientation);UiTestSupport.screenshot(a,"studio-b1-landscape-hand")}
            rule.onNodeWithTag("studio-more").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-Balanced").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-more-panel-close").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
            assertNotNull("saved once, used in portrait and landscape",
                StudioStore(prefs(),STUDIO_LAYOUT_KEY,StudioProfiles(prefs(),STUDIO_LAYOUT_KEY).activeName()).load())
        } finally {s.close();StudioProfiles(prefs(),STUDIO_LAYOUT_KEY).resetAll();StudioProfiles(prefs(),"landscape").resetAll()}
    }

    @Test fun ownerHandDesignAppliesUnchangedFromTheHandScreen() {
        val s=launch()
        try {
            rule.onNodeWithTag("home-open-studio").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-tab-screen-Hand").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-more").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-owner").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-more-panel-close").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
            val owner=context.assets.open("studio/owner-hand.json").bufferedReader().use{StudioLayout.parse(it.readText())}
            val store=StudioProfiles(prefs(),"portrait").activeStore()
            assertTrue(store.enabled());assertEquals(owner,store.load())
        } finally {s.close();StudioProfiles(prefs(),"portrait").resetAll()}
    }
}

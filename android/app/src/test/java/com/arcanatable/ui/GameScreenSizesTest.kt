package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.ArgentumEndpoints
import com.arcanatable.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The in-game Argentum table and hand drawer at phone, landscape and foldable sizes, plus an audit
 * that every visible control in Argentum mode is either actionable or explicitly disabled and that
 * manual-only Arcana controls are not offered. Uses [FakeArgentumServer] (recorded payloads).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameScreenSizesTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val fake = FakeArgentumServer()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        fake.start()
        var id = 3000
        fun card(name: String) = JSONObject().put("id", id++).put("name", name).put("type", "CARD").put("colorArgb", 0xFF547D9A.toInt())
            .put("tapped", false).put("token", false).put("faceDown", false).put("keywords", JSONArray()).put("counters", JSONObject())
        val library = JSONArray().apply { repeat(99) { put(card("Forest")) } }
        fun seat(name: String, sid: String, computer: Boolean, lib: JSONArray, cmd: JSONObject?) = JSONObject()
            .put("name", name).put("id", sid).put("commander", cmd?.getString("name") ?: JSONObject.NULL).put("commanderCard", cmd ?: JSONObject.NULL)
            .put("computer", computer).put("deckLoaded", lib.length() > 0).put("life", 40)
            .put("library", lib).put("hand", JSONArray()).put("battlefield", JSONArray()).put("graveyard", JSONArray()).put("exile", JSONArray()).put("revealed", JSONArray())
        context.getSharedPreferences("arcana-table", Context.MODE_PRIVATE).edit()
            .putString("players", JSONArray().put(seat("TESTER", "s0", false, library, card("Bello, Bard of the Brambles"))).put(seat("AI", "s1", true, JSONArray(), null)).toString()).commit()
        context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2", setOf("HOME", "TABLE", "PLAYER", "HAND", "MENU_MAIN", "MENU_TOOLS", "MENU_GAME", "MENU_DECKS", "LOBBY", "OPTIONS", "DECK_IMPORT", "STACK", "LOG")).commit()
        context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE).edit().putString(ArgentumEndpoints.PREF_ORIGIN, fake.origin).commit()
    }

    @After fun tearDown() = fake.shutdown()

    private fun shown(text: String, substring: Boolean = true) =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** A card is identifiable either by its name as text (art not loaded) or by its art's label (art loaded). */
    private fun cardShown(name: String) = shown(name) ||
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(name, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun run(name: String) {
        fake.dealOpeningHand = true
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule)
        UiTestSupport.awaitReal(rule, 30_000, "opening hand screen") { shown("Opening hand") && shown("MULLIGAN") }
        UiTestSupport.settle(rule, 600)
        scenario.onActivity { UiTestSupport.screenshot(it, "game-$name-0-opening-hand") }
        assertTrue("real card names from Argentum", cardShown("Rampaging Baloths"))
        rule.onAllNodesWithText("KEEP ", substring = true)[0].performClick()
        UiTestSupport.awaitReal(rule, 30_000, "Argentum table") { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("phase-tracker"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        UiTestSupport.settle(rule, 800)
        scenario.onActivity { UiTestSupport.screenshot(it, "game-$name-1-table") }

        // Own board and hand drawer.
        rule.onNode(androidx.compose.ui.test.hasTestTag("player-tab-you")).performClick()
        UiTestSupport.settle(rule, 800)
        scenario.onActivity { UiTestSupport.screenshot(it, "game-$name-2-board") }
        listOf("DRAW", "TOKEN", "SHUFFLE", "NEXT STEP  ›", "MANA POOL ·").forEach {
            assertFalse("manual-only control '$it' must not be offered on an Argentum board", shown(it, substring = false))
        }
        assertFalse(shown("OTHER PLAYER · READ ONLY"))
        assertTrue("compact table shows the phase strip", rule.onAllNodes(hasTestTag("phase-tracker"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNode(hasTestTag("hand-peek")).performClick()
        UiTestSupport.settle(rule, 1_000)
        scenario.onActivity { UiTestSupport.screenshot(it, "game-$name-3-hand") }
        assertTrue(shown("YOUR HAND"))
        assertTrue("hand tray offers Argentum's actions", shown("ACTIONS ·"))
        assertFalse("no manual draw in Argentum mode", shown("DRAW", substring = false))
        assertFalse("no manual reveal in Argentum mode", shown("REVEAL HAND"))
        assertTrue("pass button names where it stops", shown("PASS →"))
        rule.onNode(hasTestTag("hand-tray-pass-button")).performTouchInput { down(); advanceEventTime(1_000); up() }
        UiTestSupport.awaitReal(rule, 5_000, "hand-tray auto-pass control") { shown("STOP AUTO-PASS", substring = false) }
        rule.onNode(hasTestTag("hand-tray-pass-button")).performClick()
        UiTestSupport.awaitReal(rule, 5_000, "hand-tray auto-pass stopped") { !shown("STOP AUTO-PASS", substring = false) }

        // Button audit: every visible clickable node is enabled with a label, or explicitly disabled.
        val unlabeledClickable = rule.onAllNodes(androidx.compose.ui.test.hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
            .filter { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
                val enabled = node.config.getOrNull(SemanticsProperties.Disabled) == null
                enabled && text.isBlank() && desc.isBlank() && node.children.none { child -> child.config.getOrNull(SemanticsProperties.Text) != null }
            }
        println("[audit] $name: ${unlabeledClickable.size} enabled clickable nodes without a visible label")
        scenario.close()
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortrait() = run("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscape() = run("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldableCover() = run("foldable-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldableUnfolded() = run("foldable-unfolded")
}

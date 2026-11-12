package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.ArgentumEndpoints
import com.arcanatable.ArgentumTableSnapshot
import com.arcanatable.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** J10 fixture rendering on its integration base; not proof of Studio editing or phone FPS. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioBusyFixtureUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val fake = FakeArgentumServer()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        fake.stateMessage = FakeArgentumServer.fixture("studio-busy-four-player.json")
        fake.dealOpeningHand = false
        fake.start()
        var id = 9000
        fun card(name: String) = JSONObject().put("id", id++).put("name", name).put("type", "CARD")
            .put("colorArgb", 0xFF547D9A.toInt()).put("tapped", false).put("token", false)
            .put("faceDown", false).put("keywords", JSONArray()).put("counters", JSONObject())
        val library = JSONArray().apply { repeat(99) { put(card("Forest")) } }
        val seat = JSONObject().put("name", "J10 TESTER").put("id", "s0")
            .put("commander", "Bello, Bard of the Brambles").put("commanderCard", card("Bello, Bard of the Brambles"))
            .put("computer", false).put("deckLoaded", true).put("life", 40).put("library", library)
        listOf("hand", "battlefield", "graveyard", "exile", "revealed").forEach { seat.put(it, JSONArray()) }
        val opponent = JSONObject(seat.toString()).put("id", "s1").put("name", "AI")
            .put("computer", true).put("deckLoaded", false).put("library", JSONArray())
            .put("commander", JSONObject.NULL).put("commanderCard", JSONObject.NULL)
        context.getSharedPreferences("arcana-table", Context.MODE_PRIVATE).edit()
            .putString("players", JSONArray().put(seat).put(opponent).toString()).commit()
        context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2", setOf("HOME", "TABLE", "PLAYER", "HAND", "MENU_MAIN", "MENU_TOOLS", "MENU_GAME", "MENU_DECKS", "LOBBY", "OPTIONS", "DECK_IMPORT", "STACK", "LOG")).commit()
        context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE).edit()
            .putString(ArgentumEndpoints.PREF_ORIGIN, fake.origin).commit()
    }

    @After fun tearDown() = fake.shutdown()

    private fun capture(name: String) {
        val snapshot = ArgentumTableSnapshot.parse(fake.stateMessage.getJSONObject("state"))
        assertEquals(4, snapshot.players.size)
        assertEquals(6, snapshot.stack.size)
        assertEquals(18, snapshot.players[snapshot.viewingPlayer].commanderDamageReceived["e100"])
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            UiTestSupport.settle(rule)
            UiTestSupport.playWithArgentum(rule, scenario)
            UiTestSupport.awaitReal(rule, 30_000, "crowded four-seat table") {
                rule.onAllNodes(hasTestTag("phase-tracker"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                    listOf("player-tab-you", "player-tab-0", "player-tab-2", "player-tab-3").all { tag ->
                        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == 1
                    }
            }
            UiTestSupport.settle(rule, 800)
            scenario.onActivity { activity ->
                val controller = checkNotNull(activity.tableController)
                assertEquals(4, controller.players.size)
                val you = controller.players[controller.localSeat]
                assertEquals(18, you.battlefield.size)
                assertEquals(mapOf("e100" to 18), you.commanderDamageReceived)
                assertEquals(6, checkNotNull(controller.argentumSnapshot).stack.size)
            }
            scenario.onActivity { assertTrue(UiTestSupport.screenshot(it, "studio-j10-$name-table").length() > 0) }
            UiTestSupport.clickTag(rule, "player-tab-you")
            UiTestSupport.settle(rule, 800)
            scenario.onActivity { assertTrue(UiTestSupport.screenshot(it, "studio-j10-$name-player").length() > 0) }
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w344dp-h800dp-port-xhdpi")
    fun coverTableAndPlayer() = capture("cover")

    @Test @Config(sdk = [34], qualifiers = "w673dp-h928dp-port-xhdpi")
    fun unfoldedTableAndPlayer() = capture("unfolded")
}

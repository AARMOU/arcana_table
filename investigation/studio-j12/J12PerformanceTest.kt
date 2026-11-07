package com.arcanatable.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Opt-in UI replay measurement; no engine/AI throughput or device frame-time claim. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w673dp-h841dp-port-xhdpi")
class J12PerformanceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun pump() {
        rule.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
    }
    private fun recompositions(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    @Test fun busyBoardReplay() {
        val output = File("build/j12").apply { mkdirs() }
        val fixture = FakeArgentumServer.fixture("studio-busy-four-player.json")
        val snapshot = ArgentumTableSnapshot.parse(fixture.getJSONObject("state"))
        // Same predecoded placeholder on both revisions: exclude external image I/O from this UI baseline.
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff405060.toInt()) }
        val allCards = snapshot.players.flatMap { it.hand + it.library + it.battlefield + it.graveyard + it.exile + listOfNotNull(it.commanderCard) }
        val urls = allCards.flatMap { listOfNotNull(it.imageUrl, it.artCropUrl) } + snapshot.stack.mapNotNull { it.imageUrl }
        urls.distinct().forEach { CardBitmapCache.put(it, image) }
        assertEquals(4, snapshot.players.size)
        assertEquals(6, snapshot.stack.size)
        assertTrue(snapshot.players.maxOf { it.battlefield.size } >= 18)
        listOf("arcana_options", "arcana-table", "argentum_session", "arcana_tutorial").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2", setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        rule.mainClock.autoAdvance = false
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            pump()
            scenario.onActivity { a -> a.tableController!!.apply {
                autoOpenHand = false
                argentumEnabled = true
                argentumOffline = false // synthetic replay: do not start a live engine or connect a socket
                argentumStatus = ArgentumConnectionStatus.CONNECTED
                argentumVsAiLobby = true
                argentumSnapshot = snapshot
                players = snapshot.players
                localSeat = snapshot.viewingPlayer
                selectedPlayer = snapshot.viewingPlayer
                activePlayer = snapshot.activePlayer
                turnNumber = snapshot.turnNumber
                gameStartedOnline = true
                homeOpen = false
                lobbyOpen = false
                gameTimerRunning = false
                turnTimerRunning = false
                showOverview = true
                showHand = false
            } }
            repeat(8) { pump() }
            assertTrue("real native table rendered", rule.onAllNodesWithTag("phase-tracker", useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
            J12Mode.applyEdit(scenario) { pump() }
            val results = JSONObject().put("mode", J12Mode.name).put("fixturePlayers", 4)
                .put("fixtureStack", snapshot.stack.size).put("qualifiers", "w673dp-h841dp-port-xhdpi")
                .put("method", "direct parsed-snapshot replay; 16ms virtual clock per update; wall time includes UI harness; no network/engine/AI")
            for (overview in listOf(true, false)) {
                val label = if (overview) "table" else "player"
                scenario.onActivity { it.tableController!!.apply { showOverview = overview; showHand = false } }
                repeat(8) { pump() }
                scenario.onActivity { assertFalse("hand must not obscure measured board",it.tableController!!.showHand) }
                // Warm the JIT and layout before starting either timing or CPU sampling.
                repeat(200) { index ->
                    scenario.onActivity { a -> a.tableController!!.apply {
                        val changed=snapshot.players.mapIndexed { i,p -> if(i==snapshot.viewingPlayer)p.copy(life=400+index%100) else p }
                        argentumSnapshot=snapshot.copy(players=changed); players=changed
                    } }
                    pump()
                }
                val before = recompositions()
                // Android's compile bootclasspath omits JFR; use the host JDK's public API reflectively.
                val hostLoader = ClassLoader::class.java.getMethod("getPlatformClassLoader").invoke(null) as ClassLoader
                val cls = Class.forName("jdk.jfr.Recording",true,hostLoader)
                val recording = cls.getConstructor().newInstance()
                val settings = cls.getMethod("enable", String::class.java).invoke(recording,"jdk.ExecutionSample")
                Class.forName("jdk.jfr.EventSettings",true,hostLoader).getMethod("withPeriod",Duration::class.java).invoke(settings,Duration.ofMillis(1))
                cls.getMethod("enable",String::class.java).invoke(recording,"jdk.GarbageCollection")
                cls.getMethod("start").invoke(recording)
                val times = mutableListOf<Double>()
                repeat(1000) { index ->
                    val start = System.nanoTime()
                    scenario.onActivity { a -> a.tableController!!.apply {
                        val changed = snapshot.players.mapIndexed { i,p -> if (i == snapshot.viewingPlayer) p.copy(life=200+index%100) else p }
                        argentumSnapshot = snapshot.copy(players=changed)
                        players = changed
                    } }
                    pump()
                    times += (System.nanoTime()-start)/1e6
                }
                val after = recompositions()
                cls.getMethod("stop").invoke(recording)
                val jfr = File(output, "$label.jfr")
                cls.getMethod("dump",java.nio.file.Path::class.java).invoke(recording,jfr.toPath())
                cls.getMethod("close").invoke(recording)
                val sorted=times.sorted()
                val row=JSONObject().put("samplesMs",JSONArray(times)).put("medianMs",(sorted[sorted.size/2-1]+sorted[sorted.size/2])/2)
                    .put("p95Ms",sorted[((sorted.size*0.95).toInt()-1).coerceAtLeast(0)])
                    .put("recomposerChangeCount",after-before).put("measuredUpdates",1000).put("warmupUpdates",200)
                    .put("jfrFile",jfr.name)
                results.put(label,row)
                scenario.onActivity { UiTestSupport.screenshot(it,"j12-${J12Mode.name}-$label") }
            }
            assertTrue("replay actually recomposed",results.getJSONObject("table").getLong("recomposerChangeCount")>0)
            File(output,"results.json").writeText(results.toString(2))
            println("J12_RESULT "+results.toString())
        } finally { scenario.close() }
    }
}

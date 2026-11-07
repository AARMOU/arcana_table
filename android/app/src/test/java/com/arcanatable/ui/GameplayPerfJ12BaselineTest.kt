package com.arcanatable.ui

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.ArgentumEndpoints
import com.arcanatable.MainActivity
import com.arcanatable.StudioControlStyle
import com.arcanatable.StudioControlsRuntime
import com.arcanatable.handleArgentumMessage
import com.arcanatable.isAction
import com.arcanatable.requiredNativeAction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * STUDIO-J12 speed baseline (Claude B, covering Crayola's stalled job; owner Nov 6 note 32).
 *
 * Question: does Arcana Studio slow ordinary play? One app build, one activity, the J10 busy four-player fixture,
 * and the same stream of game updates fed to five phases in an A-B-C-A-B order so warm-up drift shows up:
 *  1. unedited   Studio built in, nothing applied (runtime present, `active == false`)
 *  2. applied    one applied resize edit on a Table action (every Studio widget is live; `studioFitContents` runs)
 *  3. editing    Studio open (positions are tracked with `onGloballyPositioned` only here, note 32)
 *  4. unedited   profile reset, back to nothing applied
 *  5. applied    the same edit applied again
 * Running all phases in one build removes the engine-jar confound between revisions (Crayola, #950 19:02 UTC).
 *
 * Each update is a real `stateUpdate` (life totals and one permanent's tapped state change) handed to the app's own
 * message handler, followed by two 16 ms frames of the Compose and Robolectric clocks. A sampler thread records the
 * main thread's stack every 2 ms during measured updates.
 *
 * REPORT ONLY. Robolectric wall time on a CI runner is NOT phone FPS and nothing here is device-verified. The test
 * asserts only that each phase reached its intended Studio state and that updates reached the table; timing is
 * written to build/screenshots/j12-baseline.{md,json} and to stdout, never asserted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameplayPerfJ12BaselineTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val fake = FakeArgentumServer()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var fixture: JSONObject

    private companion object {
        const val WARMUP = 40
        const val MEASURED = 160
        const val SAMPLE_MS = 2L
        val BUCKETS = listOf("studioWidget", "studioFitContents", "onGloballyPositioned", "anyStudioCode")
    }

    @Before fun setUp() {
        fixture = FakeArgentumServer.fixture("studio-busy-four-player.json")
        fake.stateMessage = JSONObject(fixture.toString())
        fake.dealOpeningHand = false
        fake.start()
        var id = 9000
        fun card(name: String) = JSONObject().put("id", id++).put("name", name).put("type", "CARD")
            .put("colorArgb", 0xFF547D9A.toInt()).put("tapped", false).put("token", false)
            .put("faceDown", false).put("keywords", JSONArray()).put("counters", JSONObject())
        val library = JSONArray().apply { repeat(99) { put(card("Forest")) } }
        val seat = JSONObject().put("name", "J12 TESTER").put("id", "s0")
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

    /** The n-th update: every seat's life moves by ±1 and one battlefield permanent flips tapped/untapped. */
    private fun update(n: Int): JSONObject {
        val message = JSONObject(fixture.toString())
        val state = message.getJSONObject("state")
        val players = state.getJSONArray("players")
        val delta = if (n % 2 == 0) 1 else -1
        for (i in 0 until players.length()) players.getJSONObject(i).let { it.put("life", it.getInt("life") + delta) }
        val cards = state.getJSONObject("cards")
        val battlefield = cards.keys().asSequence().filter {
            cards.getJSONObject(it).optJSONObject("zone")?.optString("zoneType") == "Battlefield"
        }.sorted().toList()
        if (battlefield.isNotEmpty()) cards.getJSONObject(battlefield[n % battlefield.size]).let {
            it.put("isTapped", !it.optBoolean("isTapped"))
        }
        message.put("stateVersion", message.optLong("stateVersion") + 1 + n)
        return message
    }

    private fun frames(count: Int) = repeat(count) {
        rule.mainClock.advanceTimeBy(16)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    /** Stack sampler for the main (test) thread. Inclusive counts: a sample counts once per bucket/method it contains. */
    private class Sampler(private val target: Thread) {
        val samples = AtomicInteger()
        val buckets = ConcurrentHashMap<String, AtomicInteger>()
        val methods = ConcurrentHashMap<String, AtomicInteger>()
        val enabled = AtomicBoolean(false)
        private val stop = AtomicBoolean(false)
        private val thread = Thread({
            while (!stop.get()) {
                if (enabled.get()) record(target.stackTrace)
                try { Thread.sleep(SAMPLE_MS) } catch (_: InterruptedException) { return@Thread }
            }
        }, "j12-sampler").apply { isDaemon = true }

        fun start() = thread.start()
        fun close() { stop.set(true); thread.interrupt() }
        fun reset() { samples.set(0); buckets.clear(); methods.clear() }

        private fun record(stack: Array<StackTraceElement>) {
            if (stack.isEmpty()) return
            samples.incrementAndGet()
            val hit = HashSet<String>()
            val seen = HashSet<String>()
            for (f in stack) {
                val c = f.className; val m = f.methodName
                if (c.contains("StudioWidgetsKt") && (m.contains("studioWidget") || c.contains("studioWidget"))) hit += "studioWidget"
                if (m.contains("studioFitContents") || c.contains("studioFitContents")) hit += "studioFitContents"
                if (c.contains("OnGloballyPositioned") || m.contains("onGloballyPositioned") || c.contains("onGloballyPositioned")) hit += "onGloballyPositioned"
                if (c.startsWith("com.arcanatable.Studio")) hit += "anyStudioCode"
                if (c.startsWith("com.arcanatable.") && !c.startsWith("com.arcanatable.ui.")) seen += "${c.substringAfterLast('.').substringBefore('$')}.$m"
            }
            hit.forEach { buckets.getOrPut(it) { AtomicInteger() }.incrementAndGet() }
            seen.forEach { methods.getOrPut(it) { AtomicInteger() }.incrementAndGet() }
        }
    }

    private data class Phase(val name: String, val studioActive: Boolean, val editing: Boolean, val liveControls: Int,
        val updateMs: List<Double>, val handleMs: List<Double>, val frameMs: List<Double>, val recomposerChanges: Long,
        val samples: Int, val buckets: Map<String, Int>, val topMethods: List<Pair<String, Int>>)

    /** Compose's Recomposer change count, read reflectively so a Compose API move can't break the build. */
    private fun recomposerChanges(): Long = runCatching {
        val companion = Class.forName("androidx.compose.runtime.Recomposer").getField("Companion").get(null)
        val flow = companion.javaClass.getMethod("getRunningRecomposers").invoke(companion)
        val set = flow.javaClass.getMethod("getValue").invoke(flow) as Set<*>
        set.sumOf { info -> (info!!.javaClass.methods.first { it.name == "getChangeCount" }.apply { isAccessible = true }.invoke(info) as Number).toLong() }
    }.getOrDefault(-1L)

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).toInt()]
    }

    private fun f(v: Double) = String.format(java.util.Locale.ROOT, "%.2f", v)

    @Test @Config(sdk = [34], qualifiers = "w344dp-h800dp-port-xhdpi")
    fun studioCostOnBusyFourPlayerTable() {
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

            lateinit var runtime: StudioControlsRuntime
            var density = 2f
            scenario.onActivity { a ->
                runtime = checkNotNull(a.tableController!!.studioControlsRuntime) { "Debug unit tests build Studio in" }
                density = a.resources.displayMetrics.density
            }
            var editedId: String? = null
            var editStyle: StudioControlStyle? = null

            /** One resize edit on a non-required Table action, applied through the real Apply path. */
            fun applyOneEdit() {
                if (editedId == null) {
                    scenario.onActivity { runtime.begin() }
                    UiTestSupport.settle(rule, 600)
                    val candidates = runtime.controls.values
                        .filter { it.screen == "Table" && it.isAction && !it.requiredNativeAction && it.bounds.width > 0f && it.bounds.height > 0f }
                        .sortedBy { it.id }
                    for (c in candidates) {
                        val style = runtime.style(c.id).copy(width = c.bounds.width / density + 8f, height = c.bounds.height / density + 4f)
                        val ok = runCatching { scenario.onActivity { runtime.edit(c.id, style); runtime.apply() } }.isSuccess && !runtime.editing
                        if (ok) { editedId = c.id; editStyle = style; break }
                        scenario.onActivity { if (!runtime.editing) runtime.begin(); runtime.discard(); runtime.begin() }
                        UiTestSupport.settle(rule, 300)
                    }
                    if (runtime.editing) scenario.onActivity { runtime.cancel() }
                } else {
                    scenario.onActivity { runtime.begin(editedId); runtime.edit(editedId!!, editStyle!!); runtime.apply() }
                }
                UiTestSupport.settle(rule, 800)
                assertNotNull("found a Table action Studio could apply a resize to", editedId)
            }

            val sampler = Sampler(Thread.currentThread()).also { it.start() }
            var n = 0
            val phases = mutableListOf<Phase>()
            fun measure(name: String) {
                repeat(WARMUP) { val m = update(n++); scenario.onActivity { it.tableController!!.handleArgentumMessage(m) }; frames(2) }
                val updateMs = ArrayList<Double>(); val handleMs = ArrayList<Double>(); val frameMs = ArrayList<Double>()
                sampler.reset()
                val changesBefore = recomposerChanges()
                repeat(MEASURED) {
                    val m = update(n++)
                    sampler.enabled.set(true)
                    val t0 = System.nanoTime()
                    scenario.onActivity { it.tableController!!.handleArgentumMessage(m) }
                    val t1 = System.nanoTime()
                    frames(2)
                    val t2 = System.nanoTime()
                    sampler.enabled.set(false)
                    handleMs += (t1 - t0) / 1e6; frameMs += (t2 - t1) / 1e6; updateMs += (t2 - t0) / 1e6
                }
                val changes = recomposerChanges().let { if (it < 0 || changesBefore < 0) -1L else it - changesBefore }
                var expectedLife = -1; var shownLife = -2
                scenario.onActivity { a ->
                    val snapshot = checkNotNull(a.tableController!!.argentumSnapshot)
                    assertEquals(4, snapshot.players.size)
                    val raw = update(n - 1).getJSONObject("state").getJSONArray("players")
                    val viewing = (0 until raw.length()).first { raw.getJSONObject(it).getString("playerId") == snapshot.players[snapshot.viewingPlayer].id }
                    expectedLife = raw.getJSONObject(viewing).getInt("life"); shownLife = snapshot.players[snapshot.viewingPlayer].life
                }
                assertEquals("$name: the last update reached the table", expectedLife, shownLife)
                phases += Phase(name, runtime.active, runtime.editing, runtime.controls.size, updateMs, handleMs, frameMs, changes,
                    sampler.samples.get(), BUCKETS.associateWith { sampler.buckets[it]?.get() ?: 0 },
                    sampler.methods.entries.map { it.key to it.value.get() }.sortedByDescending { it.second }.take(12))
            }

            try {
                assertFalse("phase 1 starts with nothing applied", runtime.active)
                measure("1 unedited")
                applyOneEdit(); assertTrue(runtime.active); assertFalse(runtime.editing)
                measure("2 applied (1 edit)")
                scenario.onActivity { runtime.begin(editedId) }; UiTestSupport.settle(rule, 800); assertTrue(runtime.editing)
                measure("3 editing (Studio open)")
                scenario.onActivity { runtime.cancel(); runtime.resetProfile() }; UiTestSupport.settle(rule, 800)
                assertFalse("phase 4 is back to nothing applied", runtime.active)
                measure("4 unedited again")
                applyOneEdit(); assertTrue(runtime.active); assertFalse(runtime.editing)
                measure("5 applied again")
            } finally { sampler.close() }

            report(phases, editedId)
        }
    }

    private fun report(phases: List<Phase>, editedId: String?) {
        val md = StringBuilder()
        md.appendLine("# STUDIO-J12 speed baseline (Robolectric, report only)")
        md.appendLine()
        md.appendLine("Busy four-player J10 fixture, cover size w344dp-h800dp, ${WARMUP} warm-up + ${MEASURED} measured updates per phase, " +
            "each update = app stateUpdate handler + two 16 ms frames. Edited control: `$editedId` (resize).")
        md.appendLine("**Not phone FPS. Not device-verified.** CI-runner wall time; compare phases with each other, not with 16 ms.")
        md.appendLine()
        md.appendLine("| Phase | Studio active | Editing | Live Studio controls | Update median ms | p90 | p99 | Handler median | Frames median | Recomposer changes | Samples | studioWidget | studioFitContents | onGloballyPositioned | any Studio code |")
        md.appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        val json = JSONArray()
        for (p in phases) {
            fun pct(k: String) = if (p.samples == 0) "0" else "${p.buckets[k]} (${f(100.0 * (p.buckets[k] ?: 0) / p.samples)}%)"
            md.appendLine("| ${p.name} | ${p.studioActive} | ${p.editing} | ${p.liveControls} | ${f(percentile(p.updateMs, .5))} | ${f(percentile(p.updateMs, .9))} | ${f(percentile(p.updateMs, .99))} | " +
                "${f(percentile(p.handleMs, .5))} | ${f(percentile(p.frameMs, .5))} | ${p.recomposerChanges} | ${p.samples} | ${pct("studioWidget")} | ${pct("studioFitContents")} | ${pct("onGloballyPositioned")} | ${pct("anyStudioCode")} |")
            json.put(JSONObject().put("phase", p.name).put("studioActive", p.studioActive).put("editing", p.editing).put("liveControls", p.liveControls)
                .put("updateMs", JSONArray(p.updateMs)).put("handleMs", JSONArray(p.handleMs)).put("frameMs", JSONArray(p.frameMs))
                .put("recomposerChanges", p.recomposerChanges).put("samples", p.samples).put("buckets", JSONObject(p.buckets))
                .put("topMethods", JSONArray(p.topMethods.map { JSONObject().put("method", it.first).put("samples", it.second) })))
        }
        md.appendLine()
        for (p in phases) {
            md.appendLine("## ${p.name}: top app methods (inclusive samples of ${p.samples})")
            p.topMethods.forEach { md.appendLine("- `${it.first}` ${it.second} (${f(100.0 * it.second / p.samples.coerceAtLeast(1))}%)") }
            md.appendLine()
        }
        val dir = File(System.getProperty("arcana.screenshots") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "j12-baseline.md").writeText(md.toString())
        File(dir, "j12-baseline.json").writeText(JSONObject().put("editedId", editedId).put("warmup", WARMUP).put("measured", MEASURED)
            .put("phases", json).toString(1))
        println(md)
    }
}

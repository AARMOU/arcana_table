package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.LifeGainedAmountThisTurnComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import jdk.jfr.Recording

/** Controlled end-step regression: same fixture and outcomes as the Build 122 investigation. */
class EndStepBacklogInvestigationTest {
    private val output = File(System.getenv("REPRO_OUTPUT") ?: "build/endstep-investigation").apply { mkdirs() }
    private val caseName = System.getenv("REPRO_CASE") ?: "small"
    private fun flag(name: String, default: Boolean = true) = System.getenv(name)?.toBooleanStrictOrNull() ?: default
    private fun number(name: String, default: Int) = System.getenv(name)?.toIntOrNull() ?: default
    private fun write(name: String, value: JsonObject) { File(output, name).appendText(value.toString() + "\n") }

    @Test(timeout = 720000)
    fun compareNormalAiWithPassOnlyFromTheSameEndStep() {
        val d = scenario("Ocelot Pride", "Elspeth, Storm Slayer", "Haliya, Guided by Light", "Panharmonicon",
            "The Jolly Balloon Man", "Grizzly Bears", "Animar, Soul of Elements", "Llanowar Elves",
            "Kami of Whispered Hopes", "Spellskite")
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        val oldTokens = number("REPRO_OLD_TOKENS", 40)
        val prides = number("REPRO_PRIDES", 2)
        val recentPrides = number("REPRO_RECENT_PRIDES", 1)
        repeat(oldTokens) { d.putToken(d.player1, "Grizzly Bears") }
        repeat(prides) { i ->
            val id = if (i == 0) d.putCreatureOnBattlefield(d.player1, "Ocelot Pride") else d.putToken(d.player1, "Ocelot Pride")
            if (i >= prides - recentPrides) {
                check(i > 0) { "recent Prides must be token copies" }
                d.addComponent(id, EnteredThisTurnComponent)
            }
        }
        if (flag("REPRO_HALIYA")) d.putCreatureOnBattlefield(d.player1, "Haliya, Guided by Light")
        if (flag("REPRO_PANHARMONICON")) d.putPermanentOnBattlefield(d.player1, "Panharmonicon")
        if (flag("REPRO_ELSPETH")) {
            val id = d.putPermanentOnBattlefield(d.player1, "Elspeth, Storm Slayer")
            d.addComponent(id, CountersComponent(mapOf(CounterType.LOYALTY to 5)))
        }
        d.putCreatureOnBattlefield(d.player1, "The Jolly Balloon Man")
        for (name in listOf("Animar, Soul of Elements", "Llanowar Elves", "Kami of Whispered Hopes")) {
            d.putCreatureOnBattlefield(d.player2, name)
        }
        if (flag("REPRO_SPELLSKITE")) d.putCreatureOnBattlefield(d.player2, "Spellskite")
        repeat(3) { d.putLandOnBattlefield(d.player2, "Island") }
        d.addComponent(d.player1, PlayerCitysBlessingComponent)
        d.addComponent(d.player1, LifeGainedThisTurnComponent)
        d.addComponent(d.player1, LifeGainedAmountThisTurnComponent(3))
        d.setLifeTotal(d.player1, 40)
        d.setLifeTotal(d.player2, 40)
        val root = d.state
        val rootFingerprint = fingerprint(d)
        write("fixture.jsonl", buildJsonObject {
            put("case", caseName); put("oldTokens", oldTokens); put("prides", prides); put("recentTokenPrides", recentPrides)
            put("haliya", flag("REPRO_HALIYA")); put("panharmonicon", flag("REPRO_PANHARMONICON"))
            put("elspeth", flag("REPRO_ELSPETH")); put("spellskite", flag("REPRO_SPELLSKITE"))
            put("initialFingerprint", rootFingerprint)
            put("scope", "Synthetic controlled fixture, not an exact replay; direct-seeded token copies have printed characteristics and no Jolly delayed sacrifice")
        })
        val baseline = runMode(d, root, false)
        assertEquals(rootFingerprint, fingerprintAfterRestore(d, root))
        val normal = runMode(d, root, true)
        write("comparison.jsonl", buildJsonObject {
            put("case", caseName); put("baselineCompleted", baseline.completed); put("normalCompleted", normal.completed)
            put("sameFinalFingerprint", baseline.fingerprint == normal.fingerprint)
            put("baselineMs", baseline.elapsedMs); put("normalMs", normal.elapsedMs)
        })
        assertTrue("pass-only rules baseline must settle", baseline.completed)
        assertTrue("mobile AI must settle", normal.completed)
        assertEquals("search optimization must preserve the final game state", baseline.fingerprint, normal.fingerprint)
    }

    private fun fingerprintAfterRestore(d: GameTestDriver, root: GameState): JsonObject {
        d.replaceState(root)
        return fingerprint(d)
    }

    private fun fingerprint(d: GameTestDriver): JsonObject = buildJsonObject {
        for ((label, p) in listOf("human" to d.player1, "ai" to d.player2)) {
            put(label, buildJsonObject {
                put("life", d.getLifeTotal(p)); put("handSize", d.state.getHand(p).size)
                put("permanents", buildJsonObject {
                    d.getPermanents(p).groupingBy { d.getCardName(it) ?: "?" }.eachCount().toSortedMap().forEach { (n, c) -> put(n, c) }
                })
                put("tokens", d.getPermanents(p).count { d.state.getEntity(it)?.has<TokenComponent>() == true })
            })
        }
        put("step", d.state.step.name); put("stackSize", d.state.stack.size); put("gameOver", d.state.gameOver)
        put("pending", d.state.pendingDecision?.javaClass?.simpleName ?: "none")
    }

    private data class Result(val completed: Boolean, val elapsedMs: Long, val fingerprint: JsonObject)

    private fun runMode(d: GameTestDriver, root: GameState, useAi: Boolean): Result {
        d.replaceState(root)
        val mode = if (useAi) "normal-ai" else "pass-only"
        val bot = OfflineAiPolicy.create(d.cardRegistry, d.player2)
        val enricher = LegalActionEnricher(d.services.manaSolver, d.cardRegistry)
        val started = System.nanoTime()
        val phase = AtomicReference("start")
        val phaseStarted = AtomicLong(started)
        val stop = AtomicBoolean(false)
        val target = Thread.currentThread()
        val samples = ConcurrentHashMap<String, AtomicLong>()
        val recording = Recording().apply {
            enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10))
            enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(10))
            enable("jdk.GarbageCollection")
            setToDisk(true)
            start()
        }
        val recordingFile = File(output, "$mode.jfr").toPath()
        fun dumpSamples() {
            val text = samples.entries.sortedByDescending { it.value.get() }
                .joinToString("\n") { (stack, count) -> "${count.get()}\t$stack" }
            File(output, "$mode-wall-samples.tsv").writeText(text + "\n")
        }
        val watcher = Thread({
            var lastDump = started
            while (!stop.get()) {
                val now = System.nanoTime()
                val stack = target.stackTrace.filter { it.className.startsWith("com.wingedsheep") || it.className.startsWith("com.arcanatable") }
                val signature = phase.get() + " | " + stack.take(18).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                samples.computeIfAbsent(signature) { AtomicLong() }.incrementAndGet()
                if (now - lastDump > 5_000_000_000L) { dumpSamples(); lastDump = now }
                if (now - phaseStarted.get() > 120_000_000_000L) {
                    dumpSamples()
                    write("watchdog.jsonl", buildJsonObject {
                        put("case", caseName); put("mode", mode); put("phase", phase.get())
                        put("reason", "One operation exceeded 120 seconds; diagnostic worker halted")
                        put("elapsedMs", (now - started) / 1_000_000)
                    })
                    recording.dump(recordingFile)
                    Runtime.getRuntime().halt(124)
                }
                try { Thread.sleep(20) } catch (_: InterruptedException) { break }
            }
        }, "endstep-sampler").apply { isDaemon = true; start() }
        fun <T> measure(name: String, work: () -> T): Pair<T, Long> {
            phase.set(name); phaseStarted.set(System.nanoTime())
            val t = System.nanoTime()
            val value = work()
            return value to (System.nanoTime() - t)
        }
        var enteredEnd = false
        var completed = false
        var steps = 0
        var aiCalls = 0
        var aiNanos = 0L
        var maxAiNanos = 0L
        var liveNanos = 0L
        var humanLegalNanos = 0L
        var maxStack = 0
        var nonPassActions = 0
        try {
            OfflineAiPolicy.withThinkingBurst {
            while (steps < 30000 && System.nanoTime() - started < 240_000_000_000L) {
                if (d.state.step == Step.END) enteredEnd = true
                if (enteredEnd && d.state.step != Step.END) { completed = true; break }
                if (d.state.gameOver) { completed = true; break }
                maxStack = maxOf(maxStack, d.state.stack.size)
                val pending = d.state.pendingDecision
                if (pending != null) {
                    val (_, nanos) = measure("live-decision") { d.decide() }
                    liveNanos += nanos
                    steps++
                    continue
                }
                val who = d.state.priorityPlayerId ?: error("No priority and no decision")
                val stackBefore = d.state.stack.size
                val battlefieldBefore = d.state.getBattlefield().size
                val action: GameAction = if (who == d.player2 && useAi) {
                    val (chosen, nanos) = measure("ai-choice") { OfflineAiPolicy.think { bot.chooseAction(d.state) } }
                    aiCalls++; aiNanos += nanos; maxAiNanos = maxOf(maxAiNanos, nanos)
                    if (chosen !is PassPriority) nonPassActions++
                    write("ai-calls.jsonl", buildJsonObject {
                        put("case", caseName); put("mode", mode); put("call", aiCalls); put("elapsedMs", nanos / 1_000_000)
                        put("stackBefore", stackBefore); put("battlefieldBefore", battlefieldBefore)
                        put("action", chosen.javaClass.simpleName); put("detail", chosen.toString())
                    })
                    chosen
                } else {
                    if (who == d.player1) {
                        val (_, nanos) = measure("human-legal-actions") {
                            enricher.enrich(d.legalActions(who), d.state, who)
                        }
                        humanLegalNanos += nanos
                    }
                    PassPriority(who)
                }
                val (result, nanos) = measure("live-action") { d.submit(action) }
                liveNanos += nanos
                assertNull("$mode rejected $action", result.error)
                steps++
                if (steps % 50 == 0) {
                    write("progress.jsonl", buildJsonObject {
                        put("case", caseName); put("mode", mode); put("steps", steps); put("aiCalls", aiCalls)
                        put("elapsedMs", (System.nanoTime() - started) / 1_000_000)
                        put("stackSize", d.state.stack.size); put("battlefield", d.state.getBattlefield().size)
                        put("aiMs", aiNanos / 1_000_000); put("liveMs", liveNanos / 1_000_000)
                    })
                }
                phase.set("between-operations"); phaseStarted.set(System.nanoTime())
            }
            }
            if (useAi) {
                assertEquals("targetless cascade must not activate Spellskite", 0, nonPassActions)
                assertTrue("AI work must remain bounded: ${aiNanos / 1_000_000}ms", aiNanos < 5_000_000_000L)
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val finalFingerprint = fingerprint(d)
            write("summary.jsonl", buildJsonObject {
                put("case", caseName); put("mode", mode); put("completed", completed); put("elapsedMs", elapsed)
                put("steps", steps); put("aiCalls", aiCalls); put("aiMs", aiNanos / 1_000_000)
                put("slowestAiMs", maxAiNanos / 1_000_000); put("liveMs", liveNanos / 1_000_000)
                put("humanLegalMs", humanLegalNanos / 1_000_000); put("maxStack", maxStack)
                put("nonPassAiActions", nonPassActions); put("finalFingerprint", finalFingerprint)
            })
            return Result(completed, elapsed, finalFingerprint)
        } finally {
            stop.set(true); watcher.interrupt(); watcher.join(2000)
            dumpSamples()
            samples.entries.sortedByDescending { it.value.get() }.take(16).forEach { (location, hits) ->
                println("BUILD127_JFR_TOP case=$caseName mode=$mode hits=${hits.get()} sample=$location")
            }
            recording.stop(); recording.dump(recordingFile); recording.close()
        }
    }
}

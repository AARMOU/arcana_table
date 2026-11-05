package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.AiDifficulty
import com.arcanatable.engine.ai.AiPriorityChoice
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil

/** Diagnostic samples of frozen synthetic main-phase states, not the owner's missing replay. */
class Ai954PriorityProfileTest {
    @Test(timeout = 240_000)
    fun sampleIndependentNormalAndBrutalPriorityWindows() {
        val output = File("build/ai954-profile").apply { mkdirs() }
        val json = Json {
            encodeDefaults = true
            allowStructuredMapKeys = true
            classDiscriminator = "type"
            serializersModule = com.wingedsheep.engine.core.engineSerializersModule
        }
        for ((size, mana, endStep) in listOf(Triple(40, 100, false), Triple(200, 100, false), Triple(40, 0, false), Triple(200, 0, false), Triple(40, 0, true), Triple(200, 0, true))) {
            val d = scenario()
            if (endStep) d.passPriorityUntil(com.wingedsheep.sdk.core.Step.END)
            repeat(size) { d.putToken(d.player1, "Clue") }
            if (mana > 0) d.giveColorlessMana(d.player1, mana)
            val root = d.state.copy(turnNumber = 20)
            val encoded = json.encodeToString(GameState.serializer(), root)
            File(output, "state-$size-mana$mana-end$endStep.json").writeText(encoded)
            val hash = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val enumerator = LegalActionEnumerator(d.cardRegistry, d.services.manaSolver,
                d.services.costCalculator, d.services.predicateEvaluator,
                d.services.conditionEvaluator, d.services.turnManager)
            for (level in listOf(AiDifficulty.NORMAL, AiDifficulty.BRUTAL)) {
                val bot = OfflineAiPolicy.create(d.cardRegistry, d.player1, level)
                val times = mutableListOf<Double>()
                val enumTimes = mutableListOf<Double>()
                var passes = 0
                var candidateCount = 0
                repeat(13) { sample ->
                    d.replaceState(root)
                    var enumerateNanos = 0L
                    val started = System.nanoTime()
                    val chosen = OfflineAiPolicy.withThinkingBurst(level.burstMillis) {
                        OfflineAiPolicy.think {
                            AiPriorityChoice.choose(bot, level, root, d.player1, { mode ->
                                val before = System.nanoTime()
                                enumerator.enumerate(root, d.player1, mode).also {
                                    enumerateNanos += System.nanoTime() - before
                                    candidateCount = it.size
                                }
                            })
                        }
                    }
                    val elapsed = (System.nanoTime() - started) / 1_000_000.0
                    assertEquals("AI choice must not mutate its input", encoded,
                        json.encodeToString(GameState.serializer(), root))
                    assertNull("Every sampled choice must be accepted by the real engine", d.submit(chosen).error)
                    if (sample > 0) {
                        times += elapsed
                        enumTimes += enumerateNanos / 1_000_000.0
                        if (chosen is PassPriority) passes++
                    }
                }
                fun percentile(values: List<Double>, p: Double) = values.sorted()[ceil(values.size * p).toInt() - 1]
                println("AI954_PROFILE level=$level turn=20 step=${root.step} clues=$size mana=$mana " +
                    "samples=${times.size} passes=$passes candidates=$candidateCount " +
                    "p50Ms=${percentile(times, .5)} p95Ms=${percentile(times, .95)} " +
                    "enumP50Ms=${percentile(enumTimes, .5)} enumP95Ms=${percentile(enumTimes, .95)} stateSha=$hash")
                File(output, "$size-mana$mana-end$endStep-$level-samples.csv").writeText("wallMs,enumerateMs\n" +
                    times.indices.joinToString("\n") { "${times[it]},${enumTimes[it]}" })
                assertEquals(12, times.size)
                if (endStep) assertEquals("Unaffordable end-step controls must actually pass", 12, passes)
            }
        }
    }
}

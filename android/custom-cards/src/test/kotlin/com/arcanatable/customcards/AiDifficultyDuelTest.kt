package com.arcanatable.customcards

import com.arcanatable.engine.ai.AiDifficulty
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in AI-vs-AI duels between two difficulty levels on real premade Commander decks
 * (ARCANA_AI_DUEL=1, ARCANA_AI_DUEL_LEVELS=HARD,NORMAL, ARCANA_AI_DUEL_PAIRS, ARCANA_AI_DUEL_PAIR_START).
 * Each deck pair is played twice with the levels swapped between seats, same seed.
 * Writes build/reports/ai-difficulty/<a>-vs-<b>-<start>.txt. Skipped in normal test runs.
 */
class AiDifficultyDuelTest {
    @Test
    fun duel() {
        if (System.getenv("ARCANA_AI_DUEL") != "1") return
        val assets = File(System.getProperty("arcana.assets")).parentFile
        val (la, lb) = (System.getenv("ARCANA_AI_DUEL_LEVELS") ?: "HARD,NORMAL").split(",")
        val a = AiDifficulty.fromName(la); val b = AiDifficulty.fromName(lb)
        val pairs = (System.getenv("ARCANA_AI_DUEL_PAIRS") ?: "4").toInt()
        val pairStart = (System.getenv("ARCANA_AI_DUEL_PAIR_START") ?: "0").toInt()
        val maxMs = (System.getenv("ARCANA_AI_DUEL_MAX_GAME_MS") ?: "600000").toLong()
        val report = StringBuilder()
        val index = File(assets, "argentum/all-cards.index.tsv").readLines().filter { it.isNotBlank() }
            .map { val t = it.indexOf('\t'); if (t < 0) it to "card" else it.substring(0, t) to it.substring(t + 1) }
        val byLower = index.associate { it.first.lowercase() to it.first }
        fun canon(n: String): String? = byLower[n.trim().lowercase()] ?: byLower[n.trim().lowercase().substringBefore(" // ")]
        val decks = Json.parseToJsonElement(File(assets, "argentum/commander-decks.json").readText()).jsonArray.map { d ->
            val o = d.jsonObject
            val cards = o["cards"]!!.jsonObject.flatMap { (n, q) -> List(q.jsonPrimitive.int) { n } }
            Triple(o["id"]?.jsonPrimitive?.content ?: o["name"]!!.jsonPrimitive.content, o["commander"]!!.jsonPrimitive.content, cards)
        }.filter { (_, cmd, cards) -> canon(cmd) != null && cards.size == 100 && cards.all { canon(it) != null } }
            .map { (id, cmd, cards) -> Triple(id, canon(cmd)!!, cards.map { canon(it)!! }) }
        report.appendLine("eligible decks: ${decks.size}")
        var aWins = 0; var bWins = 0; var other = 0
        val lat = mutableMapOf<String, MutableList<Long>>()
        for (pair in pairStart until pairStart + pairs) {
            val picked = listOf(decks[(pair * 2) % decks.size], decks[(pair * 2 + 1) % decks.size])
            val wanted = picked.flatMap { it.third + it.second }.toSet()
            val defs = File(assets, "argentum/all-cards.jsonl").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.withIndex().mapNotNull { (i, l) ->
                    val (name, kind) = index.getOrNull(i) ?: return@mapNotNull null
                    if (kind != "card" || name in wanted) CardLoader.fromJsonPreservingIds(l) else null
                }.toList()
            }
            val registry = CardRegistry().apply { register(defs) }
            for (swap in 0..1) {
                val levels = if (swap == 0) listOf(a, b) else listOf(b, a)
                val seed = 7919L * (pair + 1)
                val services = EngineServices(registry)
                val processor = ActionProcessor(services, computeUndo = false)
                val enumerator = com.wingedsheep.engine.legalactions.LegalActionEnumerator(registry, services.manaSolver,
                    services.costCalculator, services.predicateEvaluator, services.conditionEvaluator, services.turnManager)
                val random = kotlin.random.Random(seed + swap)
                val players = picked.mapIndexed { i, (_, cmd, cards) ->
                    val list = cards.toMutableList().also { it.removeAt(it.indexOfFirst { c -> c.equals(cmd, true) }) }
                    PlayerConfig("AI $i", Deck(list), startingLife = 40, commanderCardNames = listOf(cmd), playerId = EntityId("ai-$i"))
                }
                var state = GameInitializer(registry, services.printingRegistry)
                    .initializeGame(GameConfig(players = players, skipMulligans = true, format = Format.Commander(), startingPlayerIndex = pair % 2, seed = seed)).state
                val lvl = (0..1).associate { EntityId("ai-$it") to levels[it] }
                val bots = lvl.mapValues { (id, l) -> OfflineAiPolicy.create(registry, id, l) }
                val start = System.nanoTime(); var actions = 0; var status = "timeout"
                while (!state.gameOver && state.turnNumber <= 60 && actions < 6000 && (System.nanoTime() - start) / 1_000_000 < maxMs) {
                    val decision = state.pendingDecision
                    val actor = decision?.let { state.actorFor(it.playerId) } ?: state.priorityPlayerId ?: run { status = "no-actor"; null } ?: break
                    val bot = bots[actor] ?: run { status = "no-bot"; null } ?: break
                    val t0 = System.nanoTime()
                    val action = try {
                        OfflineAiPolicy.withThinkingBurst(lvl.getValue(actor).burstMillis) {
                            OfflineAiPolicy.think {
                                if (decision != null) SubmitDecision(decision.playerId, bot.respondToDecision(state, decision)) else OfflineAiPolicy.chooseAction(bot, lvl.getValue(actor), state, random) {
                                enumerator.enumerate(state, actor, com.wingedsheep.engine.legalactions.EnumerationMode.FULL)
                            }
                            }
                        }
                    } catch (e: Throwable) { status = "ai-crash:${e.javaClass.simpleName}"; report.appendLine("  crash: $e at ${e.stackTrace.take(6).joinToString(" | ")}"); break }
                    lat.getOrPut(lvl.getValue(actor).name) { mutableListOf() } += (System.nanoTime() - t0) / 1_000_000
                    val r = processor.process(state, action).result
                    if (r.error != null) { status = "rejected"; break }
                    state = r.state; actions++
                }
                val winner = state.winnerId?.let { lvl[it] }
                if (state.gameOver) status = if (winner != null) "decisive" else "draw"
                when {
                    status != "decisive" -> other++
                    a == b -> if (state.winnerId == EntityId("ai-0")) aWins++ else bWins++
                    winner == a -> aWins++
                    else -> bWins++
                }
                report.appendLine("pair=$pair swap=$swap ${picked.map { it.second.substringBefore(',') }} seats=$levels status=$status winner=${winner ?: "-"} turn=${state.turnNumber} actions=$actions ms=${(System.nanoTime() - start) / 1_000_000}")
                println(report.lines().last { it.isNotBlank() })
            }
        }
        report.appendLine("HESITATED plays=${OfflineAiPolicy.hesitated.get()}")
        report.appendLine("RESULT ${a.name}=$aWins ${b.name}=$bWins other=$other")
        lat.forEach { (k, v) -> v.sort(); report.appendLine("LATENCY $k n=${v.size} p50=${v[v.size / 2]} p95=${v[(v.size * 95) / 100]} max=${v.last()}") }
        val dir = File("build/reports/ai-difficulty").apply { mkdirs() }
        File(dir, "${a.name}-vs-${b.name}-$pairStart.txt").writeText(report.toString())
        println(report)
        assertTrue("no games were played", aWins + bWins + other == pairs * 2)
    }
}

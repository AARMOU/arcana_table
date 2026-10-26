package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.budget.BudgetPolicy
import com.wingedsheep.ai.engine.budget.BudgetTier
import com.wingedsheep.ai.engine.budget.DecisionBudget
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Opt-in headless control-vs-candidate experiment on the bundled real Argentum engine.
 * The normal CI suite tests only the arithmetic; expensive games require ARCANA_AI_BENCHMARK=1.
 * No shipped AI policy, production assets, or runtime JARs are changed.
 */
class AiStrengthBenchmarkTest {
    private data class DeckSpec(val id: String, val commander: String, val cards: List<String>)
    private data class Sample(val side: String, val tier: String, val turn: Int, val nanos: Long)
    // No card identity, hand, deck order, target ID or raw action text in this trace.
    private data class ReplayMark(
        val index: Int, val turn: Int, val step: String, val actor: String,
        val actionKind: String, val decisionKind: String,
        val battlefieldSize: Int, val stackSize: Int, val remainingPlayers: Int,
        val tier: String, val nanos: Long,
    ) {
        fun stateAndChoice() = "$index/$turn/$step/$actor/$actionKind/$decisionKind/$battlefieldSize/$stackSize/$remainingPlayers"
        fun printable() = "n=$index turn=$turn step=$step actor=$actor action=$actionKind " +
            "decision=$decisionKind battlefield=$battlefieldSize stack=$stackSize players=$remainingPlayers " +
            "tier=$tier millis=${nanos / 1_000_000.0}"
    }
    private data class GameResult(
        val mode: Int, val seed: Long, val pair: Int, val candidateSeats: List<Int>,
        val decks: List<String>, val status: String, val winnerSide: String?,
        val turns: Int, val actions: Int, val invalidActions: Int, val priorityPasses: Int,
        val wallMs: Long, val samples: List<Sample>, val actionSequenceSha256: String,
        val marks: List<ReplayMark>, val rejectedKind: String?,
    )

    /** Observe the actual decision tier while delegating unchanged limits to the mobile policy. */
    private class TierObserver(private val fixedWorkOnly: Boolean = false) : BudgetPolicy {
        val seen = mutableSetOf<BudgetTier>()
        private fun observe(budget: DecisionBudget): DecisionBudget {
            seen += budget.tier
            if (!fixedWorkOnly) return budget
            // Test-only hypothesis: keep *the same count-based work allowances* as the mobile
            // policy, but prevent a host/runner wall clock from ending a healthy search early.
            // Even combat's secondary deadline is widened; the per-decision game timeout
            // remains in the harness. Never enable this for shipping Android.
            val allowances = budget.allowances.copy(combatSearchMillis = 60_000L)
            return DecisionBudget(budget.tier, allowances, DecisionBudget.UNBOUNDED_MILLIS)
        }
        override fun budgetFor(state: GameState, playerId: EntityId, meaningfulActions: List<LegalAction>): DecisionBudget =
            observe(OfflineAiPolicy.budgetFor(state, playerId, meaningfulActions))
        override fun budgetForDecision(state: GameState, playerId: EntityId): DecisionBudget =
            observe(OfflineAiPolicy.budgetForDecision(state, playerId))
        fun takeTier(): String {
            val tier = listOf(BudgetTier.CRITICAL, BudgetTier.NORMAL, BudgetTier.ROUTINE, BudgetTier.TRIVIAL)
                .firstOrNull { it in seen }?.name ?: "UNOBSERVED"
            seen.clear()
            return tier
        }
    }

    @Test fun arithmeticAndNeutralComparatorAreSound() {
        assertEquals(50.0, percent(2, 4), 0.0001)
        assertEquals(0.0, percent(0, 0), 0.0001)
        val ci = wilson95(100, 200)
        assertTrue(ci.first < 0.5 && ci.second > 0.5)
        assertTrue(wilson95(120, 200).first > ci.first)
        assertEquals(20L, percentile(listOf(10L, 20L, 30L), 0.5))
        assertEquals(30L, percentile(listOf(10L, 20L, 30L), 0.95))
    }

    @Test fun runOnlyWhenExplicitlyRequested() {
        if (System.getenv("ARCANA_AI_BENCHMARK") != "1") return
        val games = (System.getenv("ARCANA_AI_GAMES_PER_MODE") ?: "200").toInt()
        val modeFilter = System.getenv("ARCANA_AI_GAME_MODE")?.toInt()
        require(modeFilter == null || modeFilter in listOf(2, 4)) { "ARCANA_AI_GAME_MODE must be 2 or 4" }
        val modes = modeFilter?.let { listOf(it) } ?: listOf(2, 4)
        val pairStart = (System.getenv("ARCANA_AI_PAIR_START") ?: "0").toInt()
        require(pairStart >= 0) { "ARCANA_AI_PAIR_START must be nonnegative" }
        val maxActions = (System.getenv("ARCANA_AI_MAX_ACTIONS") ?: "4000").toInt()
        val maxWallMs = (System.getenv("ARCANA_AI_MAX_GAME_MS") ?: "120000").toLong()
        require(games > 0 && games % 2 == 0 && maxActions > 0 && maxWallMs > 0) {
            "Game count must be positive/even, action and elapsed limits positive"
        }
        val assets = File("src/main/assets")
        val lib = OfflineCardLibrary({ name -> File(assets, name).takeIf { it.isFile }?.inputStream() })
        require(lib.isFullLibrary) { "Full Argentum library is required; no Portal substitution" }
        val (eligible, excluded) = decks(lib, assets)
        require(eligible.size >= 4) { "Fewer than four fully supported decks: ${eligible.size}" }
        val fixedWorkOnly = System.getenv("ARCANA_AI_FIXED_WORK") == "1"
        println("[ai-replay-mode] fixedWorkOnly=$fixedWorkOnly (test-only; mobile unchanged)")
        val observer = TierObserver(fixedWorkOnly)
        val baseline = OfflineAiPolicy.profile.copy(budgetPolicy = observer)
        // Until a reviewed candidate exists, run control vs identical control (no strength claim).
        val candidate = OfflineAiPolicy.profile.copy(id = "arcana-ai-candidate-control", budgetPolicy = observer)
        val results = mutableListOf<GameResult>()
        val verifyReplay = System.getenv("ARCANA_AI_REPEAT_REPLAY") == "1"
        for (mode in modes) repeat(games / 2) { pairIndex ->
            val pair = pairStart + pairIndex
            val seed = SEEDS[pair % SEEDS.size] + (pair / SEEDS.size) * 1009L
            val picked = (0 until mode).map { eligible[(pair * mode + it) % eligible.size] }
                .map { d -> d.copy(commander = lib.canonical(d.commander)!!, cards = d.cards.map { lib.canonical(it)!! }) }
            val registry = CardRegistry().apply { register(lib.load(picked.flatMap { it.cards }.toSet())) }
            repeat(2) { swap ->
                // The same deck at the same seat uses each profile once for the same seed.
                val candidateSeats = (0 until mode).filter { (it + swap) % 2 == 0 }
                val result = play(mode, seed, pair, picked, candidateSeats, registry,
                    baseline, candidate, observer, maxActions, maxWallMs)
                results += result
                if (verifyReplay) {
                    // Rebuild the game and all AI instances from the same seed.
                    // This is a probe: the shipped AI remains time-budgeted.
                    val second = play(mode, seed, pair, picked, candidateSeats, registry,
                        baseline, candidate, observer, maxActions, maxWallMs)
                    println("[ai-replay] mode=$mode pair=$pair seed=$seed swap=$swap " +
                        "first=${result.actionSequenceSha256} second=${second.actionSequenceSha256} " +
                        "actions=${result.actions}/${second.actions} status=${result.status}/${second.status}")
                    val compared = minOf(result.marks.size, second.marks.size)
                    val mismatch = (0 until compared).firstOrNull {
                        result.marks[it].stateAndChoice() != second.marks[it].stateAndChoice()
                    } ?: compared.takeIf { result.marks.size != second.marks.size }
                    if (mismatch != null) {
                        val beginning = maxOf(0, mismatch - 3)
                        val ending = minOf(maxOf(result.marks.size, second.marks.size), mismatch + 4)
                        println("[ai-replay-divergence] firstCoarseMismatchIndex=$mismatch " +
                            "firstRejected=${result.rejectedKind ?: "none"} " +
                            "secondRejected=${second.rejectedKind ?: "none"}")
                        for (i in beginning until ending) {
                            println("[ai-replay-trace] n=$i first=" +
                                (result.marks.getOrNull(i)?.printable() ?: "END") +
                                " | second=" + (second.marks.getOrNull(i)?.printable() ?: "END"))
                        }
                    } else if (result.actionSequenceSha256 != second.actionSequenceSha256) {
                        println("[ai-replay-divergence] fullActionHashesDifferButCoarseChoicesMatch; " +
                            "entity IDs, action parameters or hidden state may differ. " +
                            "Do NOT conclude the AI changed its chosen action from this hash alone.")
                    }
                    assertEquals("Action stream diverged: mode=$mode pair=$pair seed=$seed swap=$swap",
                        result.actionSequenceSha256, second.actionSequenceSha256)
                    assertEquals("Game status diverged", result.status, second.status)
                    assertEquals("Action count diverged", result.actions, second.actions)
                }
                println("[ai-benchmark] ${mode}p pair=$pair swap=$swap seed=$seed status=${result.status} " +
                    "winner=${result.winnerSide} turn=${result.turns} actions=${result.actions} ms=${result.wallMs}")
            }
        }
        val dir = File(System.getenv("ARCANA_AI_REPORT_DIR") ?: "build/reports/ai-strength")
        dir.mkdirs()
        File(dir, "comparison.json").writeText(json(results, eligible.size, excluded).toString(2) + "\n")
        File(dir, "comparison.md").writeText(markdown(results, eligible.size, excluded))
        assertEquals("All paired runs are required", games * modes.size, results.size)
    }

    private fun decks(lib: OfflineCardLibrary, assets: File): Pair<List<DeckSpec>, List<String>> {
        val candidates = OfflineGameSetup.parsePremades(File(assets, "argentum/commander-decks.json").readText())
            .map { DeckSpec(it.id, it.commander, it.cards) }.toMutableList()
        val manifest = File("../../investigation/argentum-six-precons/deck-manifest.json")
        require(manifest.isFile) { "Missing six-precon manifest: ${manifest.path}" }
        val six = JSONObject(manifest.readText()).getJSONArray("decks")
        for (i in 0 until six.length()) {
            val d = six.getJSONObject(i)
            val cards = mutableListOf<String>()
            val entries = d.getJSONArray("cards")
            for (j in 0 until entries.length()) {
                val row = entries.getJSONObject(j)
                repeat(row.getInt("quantity")) { cards += row.getString("name") }
            }
            candidates += DeckSpec("six-precon:$i:${d.getString("name")}", d.getString("commander"), cards)
        }
        val excluded = mutableListOf<String>()
        val good = candidates.filter { deck ->
            val missing = (deck.cards + deck.commander).distinct().filter { lib.canonical(it) == null }
            val copies = deck.cards.count { it.equals(deck.commander, ignoreCase = true) }
            val okay = deck.cards.size == 100 && copies == 1 && missing.isEmpty()
            if (!okay) excluded += "${deck.id}: slots=${deck.cards.size}, commanderCopies=$copies, missing=${missing.joinToString()}"
            okay
        }.distinctBy { it.commander + ":" + it.cards.joinToString("|") }
        return good to excluded
    }

    private fun play(
        mode: Int, seed: Long, pair: Int, decks: List<DeckSpec>, candidateSeats: List<Int>,
        registry: CardRegistry, baseline: AiProfile, candidate: AiProfile,
        observer: TierObserver, maxActions: Int, maxWallMs: Long,
    ): GameResult {
        val services = EngineServices(registry)
        val processor = ActionProcessor(services, computeUndo = false)
        val players = decks.mapIndexed { i, deck ->
            PlayerConfig("AI ${i + 1}",
                Deck(OfflineGameSetup.argentumLibraryList(deck.cards, deck.commander)),
                startingLife = 40, commanderCardNames = listOf(deck.commander), playerId = EntityId("ai-$i"))
        }
        val cfg = GameConfig(players = players, skipMulligans = true, format = Format.Commander(),
            startingPlayerIndex = pair % mode, seed = seed)
        var state = GameInitializer(registry, services.printingRegistry).initializeGame(cfg).state
        val candidateIds = candidateSeats.map { EntityId("ai-$it") }.toSet()
        val bots = state.turnOrder.associateWith { id ->
            AIPlayer.create(registry, id, if (id in candidateIds) candidate else baseline)
        }
        val samples = mutableListOf<Sample>()
        val start = System.nanoTime()
        var actions = 0
        var invalid = 0
        var passes = 0
        var failure: String? = null
        val actionsDigest = MessageDigest.getInstance("SHA-256")
        val marks = mutableListOf<ReplayMark>()
        var rejectedKind: String? = null
        while (!state.gameOver && state.turnNumber <= 60 && actions < maxActions &&
            (System.nanoTime() - start) / 1_000_000 < maxWallMs) {
            val decision = state.pendingDecision
            val actor = decision?.let { state.actorFor(it.playerId) } ?: state.priorityPlayerId
            if (actor == null || actor !in bots) { failure = "missing-actor"; break }
            val side = if (actor in candidateIds) "candidate" else "baseline"
            observer.takeTier()
            val before = System.nanoTime()
            val action = OfflineAiPolicy.withThinkingBurst {
                OfflineAiPolicy.think {
                    if (decision != null) SubmitDecision(decision.playerId, bots.getValue(actor).respondToDecision(state, decision))
                    else bots.getValue(actor).chooseAction(state)
                }
            }
            val elapsedNanos = System.nanoTime() - before
            val tier = observer.takeTier()
            samples += Sample(side, tier, state.turnNumber, elapsedNanos)
            marks += ReplayMark(actions, state.turnNumber, state.step.toString(),
                actor.value, action.javaClass.simpleName,
                decision?.javaClass?.simpleName ?: "none",
                state.getBattlefield().size, state.stack.size, state.activePlayers.size,
                tier, elapsedNanos)
            if (action is com.wingedsheep.engine.core.PassPriority) passes++
            // Hash submitted actions in order. Expose no hidden card identities in report.
            actionsDigest.update(
                "$actions|$actor|${state.turnNumber}|${state.step}|$action\n"
                    .toByteArray(StandardCharsets.UTF_8)
            )
            val result = processor.process(state, action).result
            val rejection = result.error
            if (rejection != null) {
                rejectedKind = rejection.javaClass.simpleName + " " + action.javaClass.simpleName +
                    " turn=" + state.turnNumber + " step=" + state.step +
                    " actor=" + actor.value
                println("[ai-replay-rejected] $rejectedKind")
                invalid++
                failure = "invalid-action"
                break // Never hide an engine/AI error by silently submitting a substitute action.
            }
            state = result.state
            actions++
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        val winner = state.winnerId?.takeIf { it in bots }?.let {
            if (it in candidateIds) "candidate" else "baseline"
        }
        val status = when {
            failure != null -> failure
            state.gameOver && winner != null -> "decisive"
            state.gameOver -> "draw"
            else -> "timeout" // Turn cap, action cap or wall clock cap.
        }
        return GameResult(mode, seed, pair, candidateSeats, decks.map { it.id }, status,
            if (status == "decisive") winner else null, state.turnNumber, actions, invalid,
            passes, elapsed, samples,
            actionsDigest.digest().joinToString("") { "%02x".format(it) },
            marks, rejectedKind)
    }

    private fun json(results: List<GameResult>, eligible: Int, excluded: List<String>): JSONObject {
        val out = JSONObject().put("schema", 1).put("baselineProfile", OfflineAiPolicy.profile.id)
            .put("candidateProfile", "arcana-ai-candidate-control")
            .put("candidateIdenticalToBaseline", true).put("eligibleDecks", eligible)
            .put("excludedDecks", JSONArray(excluded))
            .put("reproducibleGameConfigSeed", true).put("productionHostBurstEquivalent", false)
            .put("fixedWorkOnly", System.getenv("ARCANA_AI_FIXED_WORK") == "1")
        out.put("games", JSONArray(results.map { g ->
            JSONObject().put("mode", g.mode).put("seed", g.seed).put("pair", g.pair)
                .put("candidateSeatsZeroBased", JSONArray(g.candidateSeats))
                .put("deckIds", JSONArray(g.decks)).put("status", g.status)
                .put("winnerSide", g.winnerSide ?: JSONObject.NULL)
                .put("turns", g.turns).put("actions", g.actions)
                .put("invalidActions", g.invalidActions).put("priorityPasses", g.priorityPasses)
                .put("wallMs", g.wallMs).put("actionSequenceSha256", g.actionSequenceSha256)
                .put("rejectedKind", g.rejectedKind ?: JSONObject.NULL)
                .put("decisions", JSONArray(g.samples.map { d ->
                    JSONObject().put("side", d.side).put("tier", d.tier)
                        .put("turn", d.turn).put("nanos", d.nanos)
                }))
        }))
        return out
    }

    private fun markdown(results: List<GameResult>, eligible: Int, excluded: List<String>): String = buildString {
        appendLine("# Arcana AI benchmark — control vs control")
        appendLine()
        appendLine("Both profiles are the **same** current Arcana AI. This is a control baseline, not improved AI.")
        appendLine("Seeded Commander games; paired games swap every profile seat without changing deck/seed.")
        appendLine("1v1: 1 candidate seat, 1 baseline seat. 4p: 2 candidate seats, 2 baseline seats.")
        appendLine("All metrics are headless JVM, not Android/Fold measurements.")
        appendLine("A 600 ms thinking burst is reset per decision here; this is NOT production host multi-decision burst timing.")
        appendLine("Complete decks: $eligible; excluded entries: ${excluded.size} (reasons in JSON).")
        appendLine()
        appendLine("| Seats | Games | Candidate wins | Baseline wins | Draws | Timeouts | Errors | Candidate win rate (95% Wilson CI, decisive only) |")
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
        for (mode in results.map { it.mode }.distinct().sorted()) {
            val games = results.filter { it.mode == mode }
            val wins = games.count { it.winnerSide == "candidate" }
            val losses = games.count { it.winnerSide == "baseline" }
            val n = wins + losses
            val ci = wilson95(wins, n)
            appendLine("| ${mode}p | ${games.size} | $wins | $losses | ${games.count { it.status == "draw" }} | ${games.count { it.status == "timeout" }} | ${games.count { it.status == "invalid-action" || it.status == "missing-actor" }} | ${fmt(percent(wins, n))}% (${fmt(ci.first * 100)}–${fmt(ci.second * 100)}%) / $n decisive |")
        }
        appendLine()
        appendLine("| Seats | Side | Actual budget tier | Decisions | p50 ms | p95 ms | p99 ms | max ms | Mean AI ms/turn | Mean game wall ms | Decisions/game |")
        appendLine("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        for (mode in results.map { it.mode }.distinct().sorted()) for (side in listOf("candidate", "baseline")) {
            val games = results.filter { it.mode == mode }
            val all = games.flatMap { it.samples.filter { s -> s.side == side } }
            val turnCount = games.sumOf { it.turns.coerceAtLeast(1) }
            val aiMsTurn = all.sumOf { it.nanos.toDouble() / 1_000_000 } / turnCount
            for (tier in all.map { it.tier }.distinct().sorted()) {
                val ms = all.filter { it.tier == tier }.map { it.nanos / 1_000_000 }
                appendLine("| ${mode}p | $side | $tier | ${ms.size} | ${percentile(ms, .5)} | ${percentile(ms, .95)} | ${percentile(ms, .99)} | ${ms.maxOrNull() ?: 0L} | ${fmt(aiMsTurn)} | ${fmt(games.map { it.wallMs }.average())} | ${fmt(all.size.toDouble() / games.size)} |")
            }
        }
        appendLine()
        appendLine("**Dumb-play tracking:** illegal AI actions and priority-pass counts are captured in JSON. Passes are NOT classified as bad plays without state context. Other requested heuristics (bad attacks/blocks, self-removal, mana waste, land discards) are *not yet instrumented*, not zero.")
        appendLine("Timeouts include bounded turn/action/wall exits; an individual blocking engine call cannot be forcibly interrupted by this JVM loop.")
        appendLine("Unfinished games and errors do not enter decisive-game win-rate denominator; their counts must be reviewed before conclusions.")
        appendLine("Do not accept policy changes without a nonidentical candidate, repeatable tests, large-board checks and the owner's device-speed check.")
    }

    companion object {
        private val SEEDS = longArrayOf(1, 7, 11, 23, 41, 73, 101, 137, 211, 307)
        private fun fmt(x: Double): String = String.format(Locale.US, "%.2f", x)
        private fun percent(wins: Int, n: Int) = if (n == 0) 0.0 else wins * 100.0 / n
        private fun wilson95(wins: Int, n: Int): Pair<Double, Double> {
            if (n == 0) return 0.0 to 1.0
            val z = 1.959963984540054
            val p = wins.toDouble() / n
            val den = 1.0 + z * z / n
            val mid = (p + z * z / (2 * n)) / den
            val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / den
            return (mid - half).coerceAtLeast(0.0) to (mid + half).coerceAtMost(1.0)
        }
        private fun percentile(values: List<Long>, q: Double): Long =
            if (values.isEmpty()) 0L else values.sorted()[(ceil(q * values.size).toInt() - 1).coerceIn(0, values.lastIndex)]
    }
}

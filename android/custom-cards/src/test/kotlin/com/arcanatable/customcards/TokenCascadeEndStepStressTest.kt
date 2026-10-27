package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.AiCascadeThrottle
import com.arcanatable.engine.ai.AiDifficulty
import com.arcanatable.engine.ai.AiPriorityChoice
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.arcanatable.engine.priority.OfflineHumanAutoPass
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.LifeGainedAmountThisTurnComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Four-seat token cascade at the human's end step, modelled on a Build 138 device report
 * (Ocelot Pride + Exalted Sunborn + life-gain triggers, three AI opponents holding instants).
 *
 * Drives every priority window the way LocalArgentumHost does and compares:
 *  - baseline: a full AI search per AI window (Build 138 behaviour);
 *  - throttled: [AiPriorityChoice] + [AiCascadeThrottle] (this change).
 * Human windows use the production [OfflineHumanAutoPass] in both modes.
 *
 * Writes timings to build/token-cascade/; sizes come from TOKEN_CASCADE_* variables.
 */
class TokenCascadeEndStepStressTest {
    private val output = File(System.getenv("TOKEN_CASCADE_OUTPUT") ?: "build/token-cascade").apply { mkdirs() }
    private fun number(name: String, default: Int) = System.getenv(name)?.toIntOrNull() ?: default
    private fun write(name: String, value: JsonObject) { File(output, name).appendText(value.toString() + "\n") }

    private class Fixture(val d: GameTestDriver, val players: List<EntityId>, val root: GameState)

    private fun fixture(): Fixture {
        val d = CardPool.driver(
            "Ocelot Pride", "Exalted Sunborn", "Soul Warden", "Grizzly Bears", "The Jolly Balloon Man",
            "Wirewood Symbiote", "Llanowar Elves", "Ghostly Prison", "Builder's Talent", "Plains", "Forest",
            "Swords to Plowshares", "Path to Exile", "Dawn's Truce", "Aang, the Last Airbender",
            "Toph, the Blind Bandit", "Raccoon Rallier", "Brambleguard Captain", "Ishgard, the Holy See",
            "Exotic Orchard",
        )
        val players = d.initMultiplayer(List(4) { Deck.of("Plains" to 60) }, startingLife = 40)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val (you, a1, a2, a3) = players
        // You: an Ocelot Pride engine with a token doubler and life-gain watchers.
        d.putCreatureOnBattlefield(you, "Ocelot Pride")
        repeat(number("TOKEN_CASCADE_RECENT_PRIDES", 1)) {
            d.putToken(you, "Ocelot Pride").also { d.addComponent(it, EnteredThisTurnComponent) }
        }
        d.putCreatureOnBattlefield(you, "Exalted Sunborn")
        repeat(number("TOKEN_CASCADE_WARDENS", 2)) { d.putCreatureOnBattlefield(you, "Soul Warden") }
        d.putCreatureOnBattlefield(you, "The Jolly Balloon Man")
        d.putCreatureOnBattlefield(you, "Wirewood Symbiote")
        d.putPermanentOnBattlefield(you, "Ghostly Prison")
        repeat(number("TOKEN_CASCADE_OLD_TOKENS", 40)) { d.putToken(you, "Grizzly Bears") }
        repeat(number("TOKEN_CASCADE_RECENT_TOKENS", 12)) {
            d.putToken(you, "Llanowar Elves").also { d.addComponent(it, EnteredThisTurnComponent) }
        }
        repeat(6) { d.putLandOnBattlefield(you, "Forest") }
        // Three AI seats, each with an instant and the mana for it, so their windows are real choices.
        d.putCreatureOnBattlefield(a1, "Aang, the Last Airbender")
        d.putCreatureOnBattlefield(a1, "Toph, the Blind Bandit")
        d.putCardInHand(a1, "Swords to Plowshares")
        d.putPermanentOnBattlefield(a2, "Builder's Talent")
        d.putCreatureOnBattlefield(a2, "Raccoon Rallier")
        d.putCreatureOnBattlefield(a2, "Brambleguard Captain")
        d.putCardInHand(a2, "Dawn's Truce")
        d.putLandOnBattlefield(a3, "Ishgard, the Holy See")
        d.putLandOnBattlefield(a3, "Exotic Orchard")
        d.putCardInHand(a3, "Path to Exile")
        for (ai in listOf(a1, a2, a3)) repeat(4) { d.putLandOnBattlefield(ai, "Plains") }
        d.addComponent(you, PlayerCitysBlessingComponent)
        d.addComponent(you, LifeGainedThisTurnComponent)
        d.addComponent(you, LifeGainedAmountThisTurnComponent(3))
        return Fixture(d, players, d.state)
    }

    private data class Result(
        val completed: Boolean, val elapsedMs: Long, val windows: Int, val aiMs: Long, val aiSearches: Int,
        val aiSkips: Int, val aiTrivial: Int, val aiActions: Int, val humanMs: Long, val humanEnumerations: Int,
        val humanStops: Int, val resolutionMs: Long, val peakStack: Int, val battlefield: Int, val tokens: Int,
    )

    private fun run(f: Fixture, throttled: Boolean): Result {
        val d = f.d
        d.replaceState(f.root)
        val you = f.players[0]
        val bots = f.players.drop(1).associateWith { OfflineAiPolicy.create(d.cardRegistry, it) }
        val enumerator = LegalActionEnumerator(d.cardRegistry, d.services.manaSolver, d.services.costCalculator,
            d.services.predicateEvaluator, d.services.conditionEvaluator, d.services.turnManager)
        val throttle = AiCascadeThrottle()
        val started = System.nanoTime()
        val limitNanos = number("TOKEN_CASCADE_LIMIT_SECONDS", 300) * 1_000_000_000L
        var windows = 0; var aiNanos = 0L; var aiSearches = 0; var aiSkips = 0; var aiTrivial = 0; var aiActions = 0
        var humanNanos = 0L; var humanEnumerations = 0; var humanStops = 0; var resolutionNanos = 0L; var peakStack = 0
        var enteredEnd = false; var completed = false
        val mode = if (throttled) "throttled" else "baseline"

        fun safePass(state: GameState, player: EntityId): GameAction = when {
            state.step == Step.DECLARE_ATTACKERS && state.isActiveTurnFor(player) &&
                state.getEntity(player)?.get<AttackersDeclaredThisCombatComponent>() == null -> DeclareAttackers(player, emptyMap())
            state.step == Step.DECLARE_BLOCKERS && !state.isActiveTurnFor(player) &&
                state.getEntity(player)?.get<BlockersDeclaredThisCombatComponent>() == null -> DeclareBlockers(player, emptyMap())
            else -> PassPriority(player)
        }

        OfflineAiPolicy.withThinkingBurst(OfflineAiPolicy.burstMillisFor(List(3) { AiDifficulty.NORMAL })) {
            // Get to your end step with priority passes only.
            while (!d.state.gameOver && System.nanoTime() - started < limitNanos) {
                val current = d.state
                if (current.step == Step.END) enteredEnd = true
                if (enteredEnd && current.step != Step.END) { completed = true; break }
                peakStack = maxOf(peakStack, current.stack.size)
                if (current.pendingDecision != null) {
                    val t = System.nanoTime(); d.decide(); resolutionNanos += System.nanoTime() - t
                    continue
                }
                val who = current.priorityPlayerId ?: error("no priority and no decision at ${current.step}")
                windows++
                val action: GameAction = if (who == you || !enteredEnd) {
                    if (who == you && enteredEnd) {
                        val t = System.nanoTime()
                        val pass = OfflineHumanAutoPass.shouldPass(
                            state = current, player = you, registry = d.cardRegistry,
                            enumerateActionsOnly = {
                                humanEnumerations++
                                enumerator.enumerate(current, you, EnumerationMode.ACTIONS_ONLY)
                            },
                        )
                        humanNanos += System.nanoTime() - t
                        if (!pass) humanStops++
                    }
                    safePass(current, who)
                } else {
                    val bot = bots.getValue(who)
                    val t = System.nanoTime()
                    val chosen = if (throttled) {
                        if (throttle.skipSearch(current, who)) { aiSkips++; safePass(current, who) }
                        else {
                            aiSearches++
                            OfflineAiPolicy.think {
                                AiPriorityChoice.choose(bot, AiDifficulty.NORMAL, current, who,
                                    enumerate = { m -> enumerator.enumerate(current, who, m) },
                                    onTrivialPass = { aiTrivial++ })
                            }.also { throttle.recordSearch(current, who, it) }
                        }
                    } else {
                        aiSearches++
                        OfflineAiPolicy.think {
                            OfflineAiPolicy.chooseAction(bot, AiDifficulty.NORMAL, current) {
                                enumerator.enumerate(current, who, EnumerationMode.FULL)
                            }
                        }
                    }
                    aiNanos += System.nanoTime() - t
                    if (chosen !is PassPriority) aiActions++
                    chosen
                }
                val t = System.nanoTime()
                val result = d.submit(action)
                if (result.error != null) d.submit(safePass(current, who))
                resolutionNanos += System.nanoTime() - t
                if (enteredEnd && windows % 100 == 0) write("progress.jsonl", buildJsonObject {
                    put("mode", mode); put("windows", windows); put("elapsedMs", (System.nanoTime() - started) / 1_000_000)
                    put("stack", d.state.stack.size); put("battlefield", d.state.getBattlefield().size)
                    put("aiMs", aiNanos / 1_000_000); put("humanMs", humanNanos / 1_000_000)
                })
            }
        }
        val battlefield = d.state.getBattlefield()
        return Result(
            completed = completed, elapsedMs = (System.nanoTime() - started) / 1_000_000, windows = windows,
            aiMs = aiNanos / 1_000_000, aiSearches = aiSearches, aiSkips = aiSkips, aiTrivial = aiTrivial,
            aiActions = aiActions, humanMs = humanNanos / 1_000_000, humanEnumerations = humanEnumerations,
            humanStops = humanStops, resolutionMs = resolutionNanos / 1_000_000, peakStack = peakStack,
            battlefield = battlefield.size,
            tokens = battlefield.count { d.state.getEntity(it)?.has<TokenComponent>() == true },
        ).also { r ->
            write("summary.jsonl", buildJsonObject {
                put("mode", mode); put("completed", r.completed); put("elapsedMs", r.elapsedMs); put("windows", r.windows)
                put("aiMs", r.aiMs); put("aiSearches", r.aiSearches); put("aiSkips", r.aiSkips); put("aiTrivialPasses", r.aiTrivial)
                put("aiActions", r.aiActions); put("humanMs", r.humanMs); put("humanEnumerations", r.humanEnumerations)
                put("humanStops", r.humanStops); put("resolutionMs", r.resolutionMs); put("peakStack", r.peakStack)
                put("battlefield", r.battlefield); put("tokens", r.tokens)
            })
            println("[token-cascade] $mode $r")
        }
    }

    @Test(timeout = 900_000)
    fun throttledAiSettlesTheSameCascadeWithFarLessSearch() {
        val f = fixture()
        // Baseline first, so the throttled run is never the one paying for JIT warm-up.
        val baseline = if (System.getenv("TOKEN_CASCADE_SKIP_BASELINE") == "true") null else run(f, throttled = false)
        val throttled = run(f, throttled = true)
        assertTrue("throttled cascade must finish the end step: $throttled", throttled.completed)
        assertTrue("the cascade must be large enough to matter: $throttled", throttled.tokens >= 100)
        assertTrue("throttle must skip most repeat searches: $throttled", throttled.aiSkips > throttled.aiSearches)
        if (baseline != null && baseline.completed) {
            assertEquals("same token outcome when no AI acts", baseline.tokens, throttled.tokens)
            assertTrue("throttled AI time ${throttled.aiMs}ms must beat baseline ${baseline.aiMs}ms",
                throttled.aiMs < baseline.aiMs)
        }
    }

    /** A spell, or anything aimed at the AI, always gets a real search; so does a changed hand. */
    @Test
    fun throttleNeverSkipsSpellsTargetsOrChangedResources() {
        val f = fixture()
        val d = f.d
        val (you, a1) = f.players
        d.replaceState(f.root)
        val throttle = AiCascadeThrottle()
        // Empty stack: never a skip.
        throttle.recordSearch(d.state, a1, PassPriority(a1))
        assertFalse(throttle.skipSearch(d.state, a1))
        // Your spell on the stack: never a skip.
        val bolt = d.putCardInHand(you, "Swords to Plowshares")
        d.giveMana(you, com.wingedsheep.sdk.core.Color.WHITE, 1)
        val target = d.findPermanent(a1, "Toph, the Blind Bandit")!!
        d.castSpellWithTargets(you, bolt, listOf(ChosenTarget.Permanent(target)))
        assertTrue(d.state.stack.isNotEmpty())
        throttle.recordSearch(d.state, a1, PassPriority(a1))
        assertFalse("spells are never skipped", throttle.skipSearch(d.state, a1))
        assertTrue("and Swords on Toph targets the AI", AiCascadeThrottle.stackTargets(d.state, a1))
        // Changed hand ends a run.
        val before = AiCascadeThrottle.resources(d.state, a1)
        d.putCardInHand(a1, "Path to Exile")
        assertNotEquals(before, AiCascadeThrottle.resources(d.state, a1))
    }
}

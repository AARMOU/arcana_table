package com.arcanatable.engine.ai

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.budget.BudgetPolicy
import com.wingedsheep.ai.engine.budget.BudgetTier
import com.wingedsheep.ai.engine.budget.DecisionBudget
import com.wingedsheep.ai.engine.budget.SearchAllowances
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import kotlin.random.Random

/**
 * Mobile search limits; leaves the engine's reference and server profiles unchanged.
 *
 * The object itself is the [AiDifficulty.NORMAL] policy (the pre-difficulty mobile AI, unchanged).
 * Other levels come from [forDifficulty]; all levels share one thinking burst per host burst.
 */
object OfflineAiPolicy : BudgetPolicy {
    private class ThinkingBurst(var remainingNanos: Long, var activeDeadline: Long? = null)
    private val thinkingBurst = ThreadLocal<ThinkingBurst>()

    /** Share optional AI search time across a host burst. Animation and rules work are uncharged. */
    fun <T> withThinkingBurst(searchMillis: Long = AiDifficulty.NORMAL.burstMillis, work: () -> T): T {
        require(searchMillis >= 0)
        if (thinkingBurst.get() != null) return work()
        thinkingBurst.set(ThinkingBurst(searchMillis * 1_000_000L))
        return try { work() } finally { thinkingBurst.remove() }
    }

    /** Thinking time for one host burst when these levels are seated (Normal-only tables keep 600 ms). */
    fun burstMillisFor(levels: Collection<AiDifficulty>): Long =
        if (levels.all { it == AiDifficulty.NORMAL }) AiDifficulty.NORMAL.burstMillis
        else maxOf(AiDifficulty.NORMAL.burstMillis, levels.sumOf { it.burstMillis })

    /** Charge only AI calls; nested decision probes retain the outer call's deadline. */
    fun <T> think(work: () -> T): T {
        val burst = thinkingBurst.get() ?: return work()
        if (burst.activeDeadline != null) return work()
        val started = System.nanoTime()
        burst.activeDeadline = started + burst.remainingNanos
        return try { work() } finally {
            burst.remainingNanos = maxOf(0L, burst.remainingNanos - (System.nanoTime() - started))
            burst.activeDeadline = null
        }
    }

    /** Milliseconds left in the current burst, or null outside one. */
    private fun burstRemainingMillis(): Long? = thinkingBurst.get()?.let { burst ->
        maxOf(0L, (burst.activeDeadline?.minus(System.nanoTime()) ?: burst.remainingNanos) / 1_000_000L)
    }

    /** One level's budget rules. [OfflineAiPolicy] delegates to the Normal one. */
    class LevelPolicy internal constructor(val difficulty: AiDifficulty) : BudgetPolicy {
        override fun budgetFor(state: GameState, playerId: EntityId, meaningfulActions: List<LegalAction>): DecisionBudget {
            if (meaningfulActions.isEmpty()) return DecisionBudget(BudgetTier.TRIVIAL, SearchAllowances.TRIVIAL_ALLOWANCES, 0)
            val combat = meaningfulActions.any { it.actionType == "DeclareAttackers" || it.actionType == "DeclareBlockers" }
            return open(state, if (combat) BudgetTier.CRITICAL else BudgetTier.NORMAL)
        }

        override fun budgetForDecision(state: GameState, playerId: EntityId): DecisionBudget = open(state, BudgetTier.NORMAL)

        private fun open(state: GameState, tier: BudgetTier): DecisionBudget {
            val crowded = state.getBattlefield().size >= AiDifficulty.CROWDED_BATTLEFIELD
            val combat = tier == BudgetTier.CRITICAL
            val millis = when {
                crowded -> difficulty.crowdedMillis
                combat -> difficulty.combatMillis
                else -> difficulty.normalMillis
            }
            val work = difficulty.allowances(combat = combat, crowded = crowded)
            val remaining = burstRemainingMillis()
            return DecisionBudget(tier, work, minOf(millis, remaining ?: millis))
        }

        override fun toString(): String = "arcana-mobile-${difficulty.name.lowercase()}"
    }

    private val levels: Map<AiDifficulty, LevelPolicy> = AiDifficulty.entries.associateWith(::LevelPolicy)

    fun policyFor(difficulty: AiDifficulty): LevelPolicy = levels.getValue(difficulty)

    /** The engine profile for [difficulty]: its strategy, the meaningful-action filter and its phone budget. */
    fun profileFor(difficulty: AiDifficulty): AiProfile = if (difficulty == AiDifficulty.NORMAL) profile else
        difficulty.baseProfile().copy(
            id = "arcana-mobile-${difficulty.name.lowercase()}",
            useMeaningfulFilter = true,
            budgetPolicy = policyFor(difficulty),
        )

    val profile: AiProfile = AiProfile.CURRENT.copy(
        id = "arcana-mobile-b123",
        useMeaningfulFilter = true,
        budgetPolicy = this,
    )

    fun create(registry: CardRegistry, player: EntityId): AIPlayer = AIPlayer.create(registry, player, profile)

    fun create(registry: CardRegistry, player: EntityId, difficulty: AiDifficulty): AIPlayer =
        AIPlayer.create(registry, player, profileFor(difficulty))

    /** How many plays an AI hesitated on (diagnostics and duel reports). */
    val hesitated = java.util.concurrent.atomic.AtomicLong()

    /**
     * One priority choice for an AI seat at [difficulty]. Levels with an [AiDifficulty.hesitateChance]
     * sometimes hold a chosen spell or ability (pass instead, only when passing is one of the [legal]
     * actions) or declare no attackers instead of a planned attack. Both are always legal.
     */
    fun chooseAction(
        bot: AIPlayer,
        difficulty: AiDifficulty,
        state: GameState,
        random: Random = Random.Default,
        legal: () -> List<LegalAction>,
    ): GameAction {
        return hesitate(bot.chooseAction(state), difficulty, state, random, legal)
    }

    /**
     * Same as [chooseAction], but the AI picks from [actions] the host already enumerated
     * (ACTIONS_ONLY, exactly what [AIPlayer.chooseAction] enumerates itself), so the board is
     * not enumerated twice.
     */
    fun chooseFrom(
        bot: AIPlayer,
        difficulty: AiDifficulty,
        state: GameState,
        actions: List<LegalAction>,
        random: Random = Random.Default,
        legal: () -> List<LegalAction>,
    ): GameAction = hesitate(bot.chooseFrom(state, actions).action, difficulty, state, random, legal)

    private fun hesitate(
        chosen: GameAction,
        difficulty: AiDifficulty,
        state: GameState,
        random: Random,
        legal: () -> List<LegalAction>,
    ): GameAction {
        if (difficulty.hesitateChance <= 0.0 || random.nextDouble() >= difficulty.hesitateChance) return chosen
        return when (chosen) {
            is DeclareAttackers -> if (chosen.attackers.isEmpty()) chosen
                else DeclareAttackers(chosen.playerId, emptyMap()).also { hesitated.incrementAndGet() }
            is CastSpell, is ActivateAbility -> {
                val options = legal()
                val manaOnly = options.any { it.action == chosen && it.isManaAbility }
                val canPass = options.any { it.actionType == "PassPriority" }
                val passer = state.priorityPlayerId
                if (manaOnly || !canPass || passer == null) chosen else PassPriority(passer).also { hesitated.incrementAndGet() }
            }
            else -> chosen
        }
    }

    override fun budgetFor(state: GameState, playerId: EntityId, meaningfulActions: List<LegalAction>): DecisionBudget =
        policyFor(AiDifficulty.NORMAL).budgetFor(state, playerId, meaningfulActions)

    override fun budgetForDecision(state: GameState, playerId: EntityId): DecisionBudget =
        policyFor(AiDifficulty.NORMAL).budgetForDecision(state, playerId)
}

package com.arcanatable.engine.ai

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.budget.BudgetPolicy
import com.wingedsheep.ai.engine.budget.BudgetTier
import com.wingedsheep.ai.engine.budget.DecisionBudget
import com.wingedsheep.ai.engine.budget.SearchAllowances
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/** Mobile search limits; leaves the engine's reference and server profiles unchanged. */
object OfflineAiPolicy : BudgetPolicy {
    private val burstDeadline = ThreadLocal<Long>()

    /** Bound optional search across a host resolution burst, including nested AI decisions. */
    fun <T> withThinkingBurst(searchMillis: Long = 600, work: () -> T): T {
        require(searchMillis >= 0)
        val previous = burstDeadline.get()
        val deadline = System.nanoTime() + searchMillis * 1_000_000L
        burstDeadline.set(minOf(previous ?: Long.MAX_VALUE, deadline))
        return try { work() } finally {
            if (previous == null) burstDeadline.remove() else burstDeadline.set(previous)
        }
    }

    val profile: AiProfile = AiProfile.CURRENT.copy(
        id = "arcana-mobile-b123",
        useMeaningfulFilter = true,
        budgetPolicy = this,
    )

    fun create(registry: CardRegistry, player: EntityId): AIPlayer = AIPlayer.create(registry, player, profile)

    override fun budgetFor(state: GameState, playerId: EntityId, meaningfulActions: List<LegalAction>): DecisionBudget {
        if (meaningfulActions.isEmpty()) return DecisionBudget(BudgetTier.TRIVIAL, SearchAllowances.TRIVIAL_ALLOWANCES, 0)
        val combat = meaningfulActions.any { it.actionType == "DeclareAttackers" || it.actionType == "DeclareBlockers" }
        return open(state, if (combat) BudgetTier.CRITICAL else BudgetTier.NORMAL)
    }

    override fun budgetForDecision(state: GameState, playerId: EntityId): DecisionBudget = open(state, BudgetTier.NORMAL)

    private fun open(state: GameState, tier: BudgetTier): DecisionBudget {
        val crowded = state.getBattlefield().size >= 96
        val millis = if (crowded) 150L else if (tier == BudgetTier.CRITICAL) 600L else 250L
        val work = if (crowded) SearchAllowances.TRIVIAL_ALLOWANCES else SearchAllowances.forMillis(millis).copy(
            targetCandidates = 2,
            refineTargetsBySimulation = false,
            blockSimulations = 4,
            attackSearchIterations = 1,
            rolloutPlayouts = 0,
        )
        val remaining = burstDeadline.get()?.let { maxOf(0L, (it - System.nanoTime()) / 1_000_000L) }
        return DecisionBudget(tier, work, minOf(millis, remaining ?: millis))
    }
}


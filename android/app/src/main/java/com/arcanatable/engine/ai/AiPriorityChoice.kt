package com.arcanatable.engine.ai

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/**
 * One AI priority choice, as the offline host makes it.
 *
 * Equivalent to [AIPlayer.chooseAction] (the same fast pass, the same ACTIONS_ONLY enumeration,
 * the same strategist), except that a window where the AI can only pass or tap for mana returns
 * at once. The strategist would otherwise sample hidden information and simulate the rest of a
 * long trigger cascade only to pass.
 */
object AiPriorityChoice {
    fun choose(
        bot: AIPlayer,
        difficulty: AiDifficulty,
        state: GameState,
        player: EntityId,
        enumerate: (EnumerationMode) -> List<LegalAction>,
        onTrivialPass: () -> Unit = {},
    ): GameAction {
        if (MeaningfulActionFilter.canAutoPassWithoutEnumerating(state, player)) return PassPriority(player)
        val actions = enumerate(EnumerationMode.ACTIONS_ONLY)
        if (AiCascadeThrottle.onlyPassOrMana(actions)) {
            onTrivialPass()
            return PassPriority(player)
        }
        return OfflineAiPolicy.chooseFrom(bot, difficulty, state, actions) { enumerate(EnumerationMode.FULL) }
    }
}

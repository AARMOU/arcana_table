package com.arcanatable.engine.priority

import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/**
 * A behavior-preserving auto-pass probe for Arcana's local human seat.
 *
 * Unlike the interactive action menu, this never needs presentation DTOs, mana-source menus,
 * or FULL auto-tap previews. Real actions are still built for every nontrivial priority window.
 * Do not reuse this for an interactive action menu, which must remain FULL + enriched.
 */
object OfflineHumanAutoPass {
    fun shouldPass(
        state: GameState,
        player: EntityId,
        registry: CardRegistry,
        enumerateActionsOnly: () -> List<LegalAction>,
        quietOpponentTurn: Boolean = false,
        stackTargetsPlayer: () -> Boolean = { false },
    ): Boolean {
        // The engine proves that these few windows pass for every possible action list.
        // Arcana local play has no stop overrides or FULL_CONTROL setting on this path.
        // Non-empty-stack windows never qualify.
        if (MeaningfulActionFilter.canAutoPassWithoutEnumerating(state, player)) return true

        // Preserve the app's Food Chain-style "Add " filtering from the old DTO path.
        val legal = enumerateActionsOnly().filterNot {
            it.actionType == "ActivateAbility" && !it.isManaAbility &&
                it.description.substringAfter(':', "").trim().startsWith("Add ", ignoreCase = true)
        }

        if (quietOpponentTurn && !state.isActiveTurnFor(player) && state.stack.isNotEmpty() &&
            legal.none { it.actionType == "DeclareBlockers" } && !stackTargetsPlayer()
        ) return true

        // LegalAction and LegalActionInfo.asPriorityAction implement the same PriorityAction
        // contract. The app's old AutoPassManager passed the DTO adaptation of this list.
        return MeaningfulActionFilter.shouldAutoPass(
            state = state,
            playerId = player,
            legalActions = legal,
            cardRegistry = registry,
        )
    }
}

package com.arcanatable.engine.priority

import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.EmblemActivatedAbilityComponent
import com.wingedsheep.engine.state.components.identity.EmblemOwnedActivatedAbilityComponent
import com.wingedsheep.engine.state.components.battlefield.ClassLevelComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.GrantStaticAbility
import com.wingedsheep.sdk.scripting.HasAllActivatedAbilitiesOfCards
import com.wingedsheep.sdk.model.EntityId

/**
 * A behavior-preserving auto-pass probe for Arcana's local human seat.
 *
 * Unlike the interactive action menu, this never needs presentation DTOs, mana-source menus,
 * or FULL auto-tap previews. Real actions are still built for every nontrivial priority window.
 * Do not reuse this for an interactive action menu, which must remain FULL + enriched.
 */
object OfflineHumanAutoPass {
    /**
     * Safe sufficient condition for the pinned engine's PASS_OWN_STACK_ITEM verdict.
     * A false result is deliberately inconclusive: fall back to full actions-only scan.
     *
     * The pinned ActivatedAbilityEnumerator is the only enumerator that emits
     * holdPriority=true, and does so only for a printed/copied/granted activated
     * ability with that property and a currently legal top-of-stack target.
     */
    internal fun canPassOwnedStackWithoutEnumerating(
        state: GameState,
        player: EntityId,
        registry: CardRegistry,
    ): Boolean {
        if (state.priorityPlayerId != player || state.pendingDecision != null || state.stack.isEmpty()) return false
        val top = state.getEntity(state.stack.last()) ?: return false
        val topController = top.get<TriggeredAbilityOnStackComponent>()?.controllerId
            ?: top.get<ActivatedAbilityOnStackComponent>()?.controllerId
            ?: top.get<SpellOnStackComponent>()?.casterId
            ?: top.get<ControllerComponent>()?.playerId
        if (topController != player) return false

        // A dynamic temporary grant might place a hold-priority activation on anything.
        if (state.grantedActivatedAbilities.any { it.ability.holdPriority }) return false
        // Emblems are off-battlefield sources, some of which grant abilities globally.
        // Fall back even for harmless emblems rather than infer their effective grants.
        if (state.entities.values.any {
                it.get<EmblemActivatedAbilityComponent>() != null ||
                it.get<EmblemOwnedActivatedAbilityComponent>() != null
            }) return false

        val definitionRisk = HashMap<Any, Boolean>()
        for (id in state.getBattlefield()) {
            val entity = state.getEntity(id) ?: return false
            val card = entity.get<CardComponent>() ?: continue
            if (card.copyActivatedAbilities.any { it.holdPriority }) return false
            if (entity.get<ClassLevelComponent>() != null) return false
            val definitionId: Any = card.cardDefinitionId
            val uncertain = definitionRisk.getOrPut(definitionId) {
                val def = registry.getCard(card.cardDefinitionId) ?: return@getOrPut true
                // Multifaced and flipped cards can expose non-front-face scripts.
                if (def.cardFaces.isNotEmpty() || def.flipSide != null) return@getOrPut true
                def.script.activatedAbilities.any { it.holdPriority } ||
                    def.script.staticAbilities.any { static ->
                        when (static) {
                            // The grant is harmless unless its embedded ability requires a stop.
                            is GrantActivatedAbility -> static.ability.holdPriority
                            // Dynamic donor/grant-of-grant paths require a real enumeration.
                            is HasAllActivatedAbilitiesOfCards, is GrantStaticAbility -> true
                            else -> false
                        }
                    }
            }
            if (uncertain) return false
        }
        return true
    }

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
        // The own-stack verdict inspects just meaningful holdPriority offers. If no source
        // in this game can create one, its result is unconditionally PASS; this check
        // costs O(permanents) rather than O(permanent x affordability/target search).
        // Fail closed for copied/granted/emblem/class/complex static ability sources.
        if (canPassOwnedStackWithoutEnumerating(state, player, registry)) return true

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

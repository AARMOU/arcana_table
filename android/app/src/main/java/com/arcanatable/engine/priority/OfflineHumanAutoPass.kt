package com.arcanatable.engine.priority

import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.legalactions.OwnStackStop
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.EmblemActivatedAbilityComponent
import com.wingedsheep.engine.state.components.identity.EmblemOwnedActivatedAbilityComponent
import com.wingedsheep.engine.state.components.identity.EmblemStaticAbilityComponent
import com.wingedsheep.engine.state.components.battlefield.ClassLevelComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.GrantStaticAbility
import com.wingedsheep.sdk.scripting.HasAllActivatedAbilitiesOfCards
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Step

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
        ownStackStops: Set<OwnStackStop> = emptySet(),
    ): Boolean {
        if (ownStackStops.isNotEmpty()) return false
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
                it.get<EmblemOwnedActivatedAbilityComponent>() != null ||
                it.get<EmblemStaticAbilityComponent>() != null
            }) return false

        val definitionRisk = HashMap<Any, Boolean>()
        for (id in state.getBattlefield()) {
            val entity = state.getEntity(id) ?: return false
            val card = entity.get<CardComponent>() ?: continue
            if (card.copyActivatedAbilities.any { it.holdPriority }) return false
            // Class level changes which printed activated/static abilities are active.
            // Include the level in the cache key instead of forcing an unconditional
            // full-board scan for every priority window containing any Class.
            val classLevel = entity.get<ClassLevelComponent>()?.currentLevel
            val definitionId: Any = card.cardDefinitionId to classLevel
            val uncertain = definitionRisk.getOrPut(definitionId) {
                // A source with no registered definition contributes no printed abilities.
                // Its copied and dynamically granted abilities were checked above.
                val def = registry.getCard(card.cardDefinitionId) ?: return@getOrPut false
                // Multifaced, flipped and transforming cards can expose a script other than the
                // front one, so every face is checked; any face that could offer a stop falls back.
                // (Failing closed on any multi-face card at all meant one opponent's Ishgard, the
                // Holy See forced a full board enumeration for each of your own triggers.)
                scriptMayHoldPriority(def.script, classLevel) ||
                    otherFaceScripts(def).any { scriptMayHoldPriority(it, null) }
            }
            if (uncertain) return false
        }
        return true
    }

    /** Whether [script] has, or could grant, an activated ability that holds priority. */
    private fun scriptMayHoldPriority(script: CardScript, classLevel: Int?): Boolean =
        script.effectiveActivatedAbilities(classLevel).any { it.holdPriority } ||
            script.effectiveStaticAbilities(classLevel).any { static ->
                when (static) {
                    // The grant is harmless unless its embedded ability requires a stop.
                    is GrantActivatedAbility -> static.ability.holdPriority
                    // Dynamic donor/grant-of-grant paths require a real enumeration.
                    is HasAllActivatedAbilitiesOfCards, is GrantStaticAbility -> true
                    else -> false
                }
            }

    /** Every script a permanent of [def] could show besides its front: faces, flip side, back face. */
    private fun otherFaceScripts(def: CardDefinition, depth: Int = 0): List<CardScript> {
        if (depth > 2) return emptyList()
        val sides = listOfNotNull(def.flipSide, def.backFace)
        return def.cardFaces.map { it.script } +
            sides.flatMap { listOf(it.script) + otherFaceScripts(it, depth + 1) }
    }

    /**
     * Opponent-owned non-spell stack objects are an unconditional STOP under the
     * pinned engine's AUTO stackVerdict, unless a persistent yield applies.
     *
     * Do not apply this in Quiet Opponent Turns, where the app deliberately auto-passes
     * un-targeted opponent objects independently of the engine's normal STOP.
     * Spell objects retain their original full enumeration: permanent spells can
     * auto-pass when the player has no valid instant-speed responses.
     */
    internal fun canStopOnOpponentNonSpellWithoutEnumerating(
        state: GameState,
        player: EntityId,
        quietOpponentTurn: Boolean = false,
    ): Boolean {
        if (state.priorityPlayerId != player || state.pendingDecision != null || state.stack.isEmpty()) return false
        if (quietOpponentTurn && !state.isActiveTurnFor(player)) return false
        val top = state.getEntity(state.stack.last()) ?: return false
        if (top.get<SpellOnStackComponent>() != null) return false
        val controller = top.get<TriggeredAbilityOnStackComponent>()?.controllerId
            ?: top.get<ActivatedAbilityOnStackComponent>()?.controllerId
            ?: top.get<ControllerComponent>()?.playerId
        if (controller == player) return false
        val identity = top.get<TriggeredAbilityOnStackComponent>()?.abilityIdentity
            ?: top.get<ActivatedAbilityOnStackComponent>()?.abilityIdentity
        if (identity != null && state.isYieldingTo(player, identity)) return false
        return true
    }

    fun shouldPass(
        state: GameState,
        player: EntityId,
        registry: CardRegistry,
        enumerateActionsOnly: () -> List<LegalAction>,
        quietOpponentTurn: Boolean = false,
        stackTargetsPlayer: () -> Boolean = { false },
        ownStackStops: Set<OwnStackStop> = emptySet(),
    ): Boolean {
        val top = state.stack.lastOrNull()?.let(state::getEntity)
        val topController = top?.get<TriggeredAbilityOnStackComponent>()?.controllerId
            ?: top?.get<ActivatedAbilityOnStackComponent>()?.controllerId
            ?: top?.get<SpellOnStackComponent>()?.casterId
            ?: top?.get<ControllerComponent>()?.playerId
        // Quiet opponent turns must not swallow a requested response to our own object.
        val quiet = quietOpponentTurn && !(topController == player && ownStackStops.isNotEmpty())
        // The engine proves that these few windows pass for every possible action list.
        // An empty-stack shortcut cannot bypass an own-stack response preference.
        // Non-empty-stack windows never qualify.
        if (MeaningfulActionFilter.canAutoPassWithoutEnumerating(state, player)) return true

        // Preserve the preexisting Quiet Opponent Turns path. When the engine is in
        // DECLARE_BLOCKERS, only enumeration can establish whether declaring is due.
        // For all other stacked windows a blockers declaration cannot be offered.
        if (quiet && !state.isActiveTurnFor(player) && state.stack.isNotEmpty() &&
            state.step != Step.DECLARE_BLOCKERS && !stackTargetsPlayer()
        ) return true

        // The engine's opponent non-spell stack verdict is STOP unless the player
        // opted to yield; no legal action enumeration is needed to establish that.
        if (canStopOnOpponentNonSpellWithoutEnumerating(state, player, quiet)) return false

        // The own-stack verdict inspects just meaningful holdPriority offers. If no source
        // in this game can create one, its result is unconditionally PASS; this check
        // costs O(permanents) rather than O(permanent x affordability/target search).
        // Fail closed for copied/granted/emblem/class/complex static ability sources.
        if (canPassOwnedStackWithoutEnumerating(state, player, registry, ownStackStops)) return true

        // Preserve the app's Food Chain-style "Add " filtering from the old DTO path.
        val legal = enumerateActionsOnly().filterNot {
            it.actionType == "ActivateAbility" && !it.isManaAbility &&
                it.description.substringAfter(':', "").trim().startsWith("Add ", ignoreCase = true)
        }

        if (quiet && !state.isActiveTurnFor(player) && state.stack.isNotEmpty() &&
            legal.none { it.actionType == "DeclareBlockers" } && !stackTargetsPlayer()
        ) return true

        // LegalAction and LegalActionInfo.asPriorityAction implement the same PriorityAction
        // contract. The app's old AutoPassManager passed the DTO adaptation of this list.
        return MeaningfulActionFilter.shouldAutoPass(
            state = state,
            playerId = player,
            legalActions = legal,
            cardRegistry = registry,
            ownStackStops = ownStackStops,
        )
    }
}

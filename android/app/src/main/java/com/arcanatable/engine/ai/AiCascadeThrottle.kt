package com.arcanatable.engine.ai

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Keeps AI seats from re-running a full priority search for every copy of the same trigger.
 *
 * A token-doubling end step can put hundreds of near-identical abilities (Ocelot Pride copies,
 * "sacrifice it at the next end step" from Balloons, life-gain triggers) on the stack. Every
 * resolution gives each AI priority again, and each full search simulates the rest of the
 * cascade, so the cost grew as (triggers x board). An AI that just looked at this exact kind of
 * ability and passed, with nothing of its own changed and nothing aimed at it, passes again
 * without searching. It looks again every [recheckEvery] windows, when the ability changes, when
 * something targets it or its permanents, and whenever its hand or its permanents change.
 *
 * Passing is always legal, so this only changes when the AI spends time thinking, never which
 * actions the rules allow. Spells on the stack are never skipped.
 */
class AiCascadeThrottle(private val recheckEvery: Int = DEFAULT_RECHECK_EVERY) {
    private class Run(val key: Any, val resources: Long, var skipped: Int = 0)

    private val runs = HashMap<EntityId, Run>()

    /** How many searches were skipped (diagnostics). */
    var skippedSearches = 0L
        private set

    fun reset() = runs.clear()

    /** True when [player] may pass this window without a search. Counts the skip. */
    fun skipSearch(state: GameState, player: EntityId): Boolean {
        val run = runs[player] ?: return false
        val key = cascadeKey(state)
        if (key == null || key != run.key || run.skipped >= recheckEvery ||
            resources(state, player) != run.resources || stackTargets(state, player)
        ) {
            runs.remove(player)
            return false
        }
        run.skipped++
        skippedSearches++
        return true
    }

    /** Remember what a real search chose, so identical windows that follow can skip theirs. */
    fun recordSearch(state: GameState, player: EntityId, chosen: GameAction) {
        val key = cascadeKey(state)
        if (chosen !is PassPriority || key == null || stackTargets(state, player)) {
            runs.remove(player)
            return
        }
        runs[player] = Run(key, resources(state, player))
    }

    companion object {
        const val DEFAULT_RECHECK_EVERY = 24

        /**
         * True when every action is passing or a mana ability: the AI's search would pass anyway
         * (its strategist only weighs affordable, non-mana candidates), so skip the search.
         */
        fun onlyPassOrMana(legal: List<LegalAction>): Boolean =
            legal.isNotEmpty() && legal.all { it.actionType == "PassPriority" || it.isManaAbility }

        /** The kind of ability on top of the stack, or null when a skip is never allowed. */
        internal fun cascadeKey(state: GameState): Any? {
            if (state.pendingDecision != null) return null
            val topId = state.stack.lastOrNull() ?: return null
            val top = state.getEntity(topId) ?: return null
            top.get<TriggeredAbilityOnStackComponent>()?.let { t ->
                return Triple(t.controllerId, t.abilityIdentity ?: t.sourceName, t.effect::class)
            }
            top.get<ActivatedAbilityOnStackComponent>()?.let { a ->
                return Triple(a.controllerId, a.sourceName, a.effect::class)
            }
            // Spells (and anything unrecognised) always get a real look.
            return null
        }

        /** Changes whenever [player]'s hand or own permanents (or their tapped state) change. */
        internal fun resources(state: GameState, player: EntityId): Long {
            var h = 1L
            for (id in state.getHand(player)) h = h * 31 + id.hashCode()
            h = h * 31 + 7
            for (id in state.getBattlefield(player)) {
                val entity = state.getEntity(id)
                h = h * 31 + id.hashCode()
                if (entity?.get<TappedComponent>() != null) h = h * 31 + 1
            }
            return h
        }

        /** Whether any object on the stack targets [player] or a permanent [player] controls. */
        internal fun stackTargets(state: GameState, player: EntityId): Boolean = state.stack.any { item ->
            state.getEntity(item)?.get<TargetsComponent>()?.targets.orEmpty().any { t ->
                when (t) {
                    is ChosenTarget.Player -> t.playerId == player
                    is ChosenTarget.Permanent ->
                        state.getEntity(t.entityId)?.get<ControllerComponent>()?.playerId == player
                    is ChosenTarget.Card -> t.ownerId == player
                    else -> false
                }
            }
        }
    }
}

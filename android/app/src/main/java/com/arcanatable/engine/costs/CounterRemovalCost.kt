package com.arcanatable.engine.costs

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * "Remove N counters of one type from among permanents you control" (Iron Spider, Stark Upgrade).
 *
 * Argentum lists the permanents that can give up counters but reports
 * distributedCounterRemovalTotal = 0, while its validator requires exactly N. Without N the
 * client can't build the distribution and the activation is rejected. This fills N in from the
 * ability's own cost, and narrows every listed permanent to the cost's counter type (as the
 * engine keys it, e.g. "+1/+1") so the client can never offer or send another type. When the
 * cost or the engine's listing is anything other than that simple shape, the action is left
 * exactly as Argentum sent it.
 */
object CounterRemovalCost {

    /** N and the counter type ("+1/+1") the client must send. */
    data class Requirement(val count: Int, val counterType: String)

    /** The ability's single fixed RemoveCounters atom (not a "this permanent" cost), or null. */
    fun requirement(cost: AbilityCost): Requirement? {
        val atoms = atoms(cost).filterIsInstance<CostAtom.RemoveCounters>()
        val atom = atoms.singleOrNull()?.takeIf { !it.self } ?: return null
        val n = (atom.count as? DynamicAmount.Fixed)?.amount?.takeIf { it > 0 } ?: return null
        val type = atom.counterType?.printed ?: return null
        return Requirement(n, type)
    }

    private fun atoms(cost: AbilityCost): List<CostAtom> = when (cost) {
        is AbilityCost.Composite -> cost.costs.flatMap(::atoms)
        is AbilityCost.Atom -> listOf(cost.atom)
        else -> emptyList()
    }

    fun fill(infos: List<LegalActionInfo>, state: GameState, registry: CardRegistry): List<LegalActionInfo> =
        infos.map { info ->
            val cost = info.additionalCostInfo
            if (cost == null || cost.costType != "RemoveCounters" || cost.distributedCounterRemovalTotal > 0 ||
                cost.counterRemovalCreatures.isEmpty()) return@map info
            val activate = info.action as? ActivateAbility ?: return@map info
            val cardId = state.getEntity(activate.sourceId)?.get<CardComponent>()?.cardDefinitionId ?: return@map info
            val ability = registry.getCard(cardId)?.script?.activatedAbilities
                ?.firstOrNull { it.id == activate.abilityId } ?: return@map info
            val need = requirement(ability.cost) ?: return@map info
            // Each permanent offers only counters of the required type, counted per type.
            val sources = cost.counterRemovalCreatures.mapNotNull { c ->
                val have = c.availableCountersByType[need.counterType] ?: 0
                if (have <= 0) null else c.copy(availableCounters = have, availableCountersByType = mapOf(need.counterType to have))
            }
            if (sources.sumOf { it.availableCounters } < need.count) return@map info
            info.copy(additionalCostInfo = cost.copy(distributedCounterRemovalTotal = need.count, counterRemovalCreatures = sources))
        }
}

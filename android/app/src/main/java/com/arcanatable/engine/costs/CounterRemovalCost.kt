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
 * "Remove N counters from among permanents you control" (Iron Spider, Stark Upgrade).
 *
 * Argentum lists the permanents that can give up counters but reports
 * distributedCounterRemovalTotal = 0, while its validator requires exactly N. Without N the
 * client can't build the distribution and the activation is rejected. This fills N in from the
 * ability's own cost so the action composer can ask which permanents to take counters from.
 */
object CounterRemovalCost {

    /** The fixed N of the ability's RemoveCounters atom (not "this permanent" costs), or null. */
    fun requiredCount(cost: AbilityCost): Int? = when (cost) {
        is AbilityCost.Composite -> cost.costs.firstNotNullOfOrNull(::requiredCount)
        is AbilityCost.Atom -> (cost.atom as? CostAtom.RemoveCounters)
            ?.takeIf { !it.self }
            ?.let { (it.count as? DynamicAmount.Fixed)?.amount }
        else -> null
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
            val n = requiredCount(ability.cost) ?: return@map info
            info.copy(additionalCostInfo = cost.copy(distributedCounterRemovalTotal = n))
        }
}

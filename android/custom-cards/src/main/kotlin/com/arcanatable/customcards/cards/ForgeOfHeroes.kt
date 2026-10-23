package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Forge of Heroes — Eldrazi Unbound.
 *
 * Targets a commander of ANY player that entered this turn. Each type is
 * evaluated independently at resolution: a creature-planeswalker commander
 * receives both kinds of counters, a noncreature/nonplaneswalker gets neither.
 */
val ForgeOfHeroes = card("Forge of Heroes") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}: Choose target commander that entered this turn. " +
        "Put a +1/+1 counter on it if it's a creature and a loyalty counter on it if it's a planeswalker."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        val commander = target(TargetFilter(
            GameObjectFilter.Permanent.commander().enteredThisTurn()
        ))
        cost = Costs.Tap
        effect = Effects.If(
            condition = Conditions.EntityMatches(commander, GameObjectFilter.Creature),
            then = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, commander)
        ) then Effects.If(
            condition = Conditions.EntityMatches(commander, GameObjectFilter.Planeswalker),
            then = Effects.AddCounters(CounterType.LOYALTY, 1, commander)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "995"
        artist = "Titus Lunter"
    }
}

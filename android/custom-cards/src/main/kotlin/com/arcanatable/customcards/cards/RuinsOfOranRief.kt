package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.ReplacementEffect
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Target must be colorless *now* and must have entered this turn. */
val RuinsOfOranRief = card("Ruins of Oran-Rief") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "This land enters tapped.\n{T}: Add {C}.\n" +
        "{T}: Put a +1/+1 counter on target colorless creature that entered this turn."

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        val creature = target(
            TargetFilter(
                GameObjectFilter.Creature
                    .withCardPredicate(CardPredicate.IsColorless)
                    .enteredThisTurn()
            )
        )
        cost = Costs.Tap
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, creature)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "1023"
        artist = "Jason Felix"
    }
}

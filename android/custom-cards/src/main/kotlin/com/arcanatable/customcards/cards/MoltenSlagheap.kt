package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** The cash-out ability does not tap; mana can be split between black and red. */
val MoltenSlagheap = card("Molten Slagheap") {
    colorIdentity = "BR"
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{1}, {T}: Put a storage counter on this land.\n" +
        "{1}, Remove X storage counters from this land: Add X mana in any combination of {B} and/or {R}."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Effects.AddCounters(CounterType.STORAGE, 1, EffectTarget.Self)
    }
    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{1}"),
            Costs.RemoveXCounters(counterType = CounterType.STORAGE, self = true),
        )
        effect = Effects.AddDynamicMana(
            DynamicAmounts.xValue(),
            allowedColors = setOf(Color.BLACK, Color.RED),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.UNCOMMON
        artist = "Daren Bader"
    }
}

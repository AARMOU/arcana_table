package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
val TendoIceBridge = card("Tendo Ice Bridge") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "This land enters with a charge counter on it.\n{T}: Add {C}.\n{T}, Remove a charge counter from this land: Add one mana of any color."
    replacementEffect(EntersWithCounters(counterType = CounterType.CHARGE, count = 1, selfOnly = true))
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.RemoveCounterFromSelf(CounterType.CHARGE, 1))
        effect = Effects.AddAnyColorMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "165"; artist = "Rob Alexander" }
}

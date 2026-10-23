package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*

val SunkenRuins = card("Sunken Ruins") {
    colorIdentity = "UB"
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{U/B}, {T}: Add {U}{U} or {B}{B}."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{U/B}"), Costs.Tap)
        effect = Effects.AddMana(Color.BLUE, 2)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{U/B}"), Costs.Tap)
        effect = Effects.AddMana(Color.BLACK, 2)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "178"
        imageUri = "https://cards.scryfall.io/normal/front/6/1/61d0e974-064e-44f4-a00b-2a2cda6ac1d5.jpg?1562915902"
    }
}

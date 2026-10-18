package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*

val UnclaimedTerritory = card("Unclaimed Territory") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "As this land enters, choose a creature type.\n{T}: Add {C}.\n{T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type."

    replacementEffect(EntersWithChoice(choiceType = ChoiceType.CREATURE_TYPE))

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddAnyColorManaSpendOnChosenType(1, creatureOnly = true)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "258"
        imageUri = "https://cards.scryfall.io/normal/front/7/8/78c36a0e-b47f-4d84-9fca-f7417e744603.jpg?1562554459"
    }
}

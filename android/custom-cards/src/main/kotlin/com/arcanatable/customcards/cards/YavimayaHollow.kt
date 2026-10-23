package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val YavimayaHollow = card("Yavimaya Hollow") {
    colorIdentity = "G"
    typeLine = "Legendary Land"
    oracleText = "{T}: Add {C}.\n{G}, {T}: Regenerate target creature."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{G}"), Costs.Tap)
        val creature = target(TargetFilter.Creature)
        effect = Effects.Regenerate(creature)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "325"
        imageUri = "https://cards.scryfall.io/normal/front/d/9/d9fdbc02-7ab7-4f77-8a89-5a9e01eb32f5.jpg?1783939212"
    }
}

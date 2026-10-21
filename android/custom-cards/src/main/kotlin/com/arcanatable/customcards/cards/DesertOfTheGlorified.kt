package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
val DesertOfTheGlorified = card("Desert of the Glorified") {
    colorIdentity = "B"
    typeLine = "Land — Desert"
    oracleText = "This land enters tapped.\n{T}: Add {B}.\nCycling {1}{B}"
    replacementEffect(EntersTapped())
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLACK)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    keywordAbility(KeywordAbility.cycling("{1}{B}"))
    metadata { rarity = Rarity.COMMON; collectorNumber = "84"; artist = "Daniel Ljunggren" }
}

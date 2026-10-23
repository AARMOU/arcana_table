package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Sea Gate Wreckage — Commander Masters #1028 (Eldrazi Unbound). */
val SeaGateWreckage = card("Sea Gate Wreckage") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{2}{C}, {T}: Draw a card. Activate only if you have no cards in hand."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}{C}"), Costs.Tap)
        effect = Effects.DrawCards(1, EffectTarget.Controller)
        restrictions = listOf(ActivationRestriction.OnlyIfCondition(Conditions.EmptyHand))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "1028"
        artist = "Zack Stella"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction

/** The second ability is land-count gated and generates genuinely restricted mana. */
val ShrineOfTheForsakenGods = card("Shrine of the Forsaken Gods") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}: Add {C}{C}. Spend this mana only to cast colorless spells. " +
        "Activate only if you control seven or more lands."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(2, restriction = ManaRestriction.ColorlessSpellsOnly)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(Conditions.ControlLandsAtLeast(7))
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "1034"
        artist = "Daniel Ljunggren"
    }
}

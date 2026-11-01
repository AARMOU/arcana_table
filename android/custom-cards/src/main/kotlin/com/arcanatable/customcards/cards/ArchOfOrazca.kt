package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Arch of Orazca — LCC #319. */
val ArchOfOrazca = card("Arch of Orazca") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the game.)\n{T}: Add {C}.\n{5}, {T}: Draw a card. Activate only if you have the city's blessing."

    keywords(Keyword.ASCEND)

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{5}"), Costs.Tap)
        effect = Effects.DrawCards(1, EffectTarget.Controller)
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(Conditions.YouHaveCitysBlessing)
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "319"
        artist = "Titus Lunter"
        imageUri = "https://cards.scryfall.io/normal/front/5/8/581dcadd-7de4-4b39-bab0-d3567194a252.jpg?1783913835"
    }
}

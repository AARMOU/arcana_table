package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Arcane Lighthouse (Commander 2014 #59).
 *
 * Needs engine patch 0043 (`cantHave`): the creatures your opponents control *as it resolves*
 * (CR 611.2c) lose hexproof and shroud, and for the turn can't have either — a later grant (Heroic
 * Intervention) or a static grant (an Aura) doesn't give it back until the turn ends (rulings
 * 2014-11-07).
 */
val ArcaneLighthouse = card("Arcane Lighthouse") {
    typeLine = "Land"
    colorIdentity = ""
    oracleText = "{T}: Add {C}.\n" +
        "{1}, {T}: Until end of turn, creatures your opponents control lose hexproof and shroud and can't have hexproof or shroud."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Patterns.Group.removeKeywordFromAll(
            Keyword.HEXPROOF,
            Filters.Group.creatures { opponentControls() },
            cantHave = true,
        ) then Patterns.Group.removeKeywordFromAll(
            Keyword.SHROUD,
            Filters.Group.creatures { opponentControls() },
            cantHave = true,
        )
        description = "{1}, {T}: Until end of turn, creatures your opponents control lose hexproof and shroud and can't have hexproof or shroud."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "59"
        artist = "Igor Kieryluk"
        imageUri = "https://cards.scryfall.io/normal/front/e/2/e2384e42-2442-4b40-9bae-8db470d2fb8c.jpg"
    }
}

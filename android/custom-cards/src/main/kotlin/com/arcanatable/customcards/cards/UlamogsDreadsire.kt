package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.WardCost

val UlamogsDreadsire = card("Ulamog's Dreadsire") {
    manaCost = "{10}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 10
    toughness = 10
    keywords(Keyword.VIGILANCE)
    keywordAbility(KeywordAbility.Ward(WardCost.Sacrifice(GameObjectFilter.Permanent.manaValueAtLeast(1), 1)))
    oracleText = "Vigilance\nWard—Sacrifice a permanent with mana value 1 or greater.\n" +
        "{T}: Create a 10/10 colorless Eldrazi creature token."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.CreateToken(
            power = 10,
            toughness = 10,
            creatureTypes = setOf("Eldrazi"),
            name = "Eldrazi",
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "38"
        artist = "Chris Rahn"
    }
}

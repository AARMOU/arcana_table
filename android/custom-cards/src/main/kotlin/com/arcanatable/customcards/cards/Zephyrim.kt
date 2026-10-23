package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

val Zephyrim = card("Zephyrim") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Warrior"
    power = 3
    toughness = 3
    oracleText = "Squad {2}\nFlying, vigilance\nMiracle {1}{W}"

    squad("{2}")
    keywords(Keyword.FLYING, Keyword.VIGILANCE)
    keywordAbility(KeywordAbility.Miracle(ManaCost.parse("{1}{W}")))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "20"
        artist = "Zezhou Chen"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
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
    oracleText = "Squad {2} (As an additional cost to cast this spell, you may pay {2} any number of times. When this creature enters, create that many tokens that are copies of it.)\nFlying, vigilance\nMiracle {1}{W} (You may cast this card for its miracle cost when you draw it if it's the first card you drew this turn.)"

    keywords(Keyword.FLYING, Keyword.VIGILANCE)
    squad("{2}")
    keywordAbility(KeywordAbility.miracle("{1}{W}"))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "20"
        artist = "Anna Steinbauer"
        imageUri = "https://cards.scryfall.io/normal/front/f/7/f78f5b9a-bd80-41e9-873e-826628dc44a7.jpg?1783920939"
    }
}

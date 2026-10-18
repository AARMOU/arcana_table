package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val FrogSquirrels = card("Frog-Squirrels") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Frog Squirrel"
    power = 2
    toughness = 2
    oracleText = "Reach (This creature can block creatures with flying.)"
    keywords(Keyword.REACH)
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "251"
        imageUri = "https://cards.scryfall.io/normal/front/7/3/73f4c4b7-5c05-48a5-adb2-ca850df8cd03.jpg?1783904773"
    }
}

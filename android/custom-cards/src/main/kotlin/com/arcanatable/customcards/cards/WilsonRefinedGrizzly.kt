package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.WardCost

val WilsonRefinedGrizzly = card("Wilson, Refined Grizzly") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Bear Warrior"
    power = 2
    toughness = 2
    cantBeCountered = true
    keywords(Keyword.VIGILANCE, Keyword.REACH, Keyword.TRAMPLE)
    keywordAbility(KeywordAbility.Ward(WardCost.Mana("{2}")))
    oracleText = "This spell can't be countered.\nVigilance, reach, trample\nWard {2}\n" +
        "Choose a Background (You can have a Background as a second commander.)"

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "261"
        artist = "Justine Mara Andersen"
        imageUri = "https://cards.scryfall.io/normal/front/d/f/dfe0daf8-43da-484b-baa1-76f8313c5a0c.jpg?1783922700"
    }
}

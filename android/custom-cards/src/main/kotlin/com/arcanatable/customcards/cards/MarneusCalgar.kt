package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val MarneusCalgar = card("Marneus Calgar") {
    manaCost = "{2}{W}{U}{B}"
    colorIdentity = "WUB"
    typeLine = "Legendary Creature — Astartes Warrior"
    power = 3
    toughness = 5
    oracleText = "Double strike\nMaster Tactician — Whenever one or more tokens you control enter, draw a card.\nChapter Master — {6}: Create two 2/2 white Astartes Warrior creature tokens with vigilance."

    keywords(Keyword.DOUBLE_STRIKE)

    triggeredAbility {
        trigger = Triggers.oneOrMore(GameObjectFilter.Token).enter()
        effect = Effects.DrawCards(1)
        description = "Whenever one or more tokens you control enter, draw a card."
    }

    activatedAbility {
        cost = Costs.Mana("{6}")
        effect = Effects.CreateToken(
            count = 2,
            power = 2,
            toughness = 2,
            colors = setOf(Color.WHITE),
            creatureTypes = setOf("Astartes", "Warrior"),
            keywords = setOf(Keyword.VIGILANCE),
            name = "Astartes Warrior",
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "8"
        artist = "Slawomir Maniak"
        imageUri = "https://cards.scryfall.io/normal/front/e/7/e7517e8e-b424-4731-ba9d-6132bdefa6bf.jpg?1783920949"
    }
}

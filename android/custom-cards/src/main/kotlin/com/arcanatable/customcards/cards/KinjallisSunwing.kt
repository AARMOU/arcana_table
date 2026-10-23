package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.PermanentsEnterTapped

val KinjallisSunwing = card("Kinjalli's Sunwing") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Dinosaur"
    power = 2
    toughness = 3
    oracleText = "Flying\nCreatures your opponents control enter tapped."
    keywords(Keyword.FLYING)

    replacementEffect(
        PermanentsEnterTapped(
            EventPattern.ZoneChangeEvent(
                filter = GameObjectFilter.Creature.opponentControls(),
                to = Zone.BATTLEFIELD,
            ),
        ),
    )

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "130"
        artist = "Simon Dominic"
        imageUri = "https://cards.scryfall.io/normal/front/0/4/04421508-b5ac-4466-9f77-dfef478d3cc2.jpg?1783913896"
    }
}

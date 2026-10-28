package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

/**
 * Chittering Dispatcher — M3C #63.
 */
val ChitteringDispatcher = card("Chittering Dispatcher") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi Drone"
    power = 2
    toughness = 3
    oracleText = "Devoid (This card has no color.)\nMyriad\nWhen this creature leaves the battlefield, create a 0/1 colorless Eldrazi Spawn creature token with \"Sacrifice this token: Add {C}.\""

    keywords(Keyword.DEVOID)

    keywords(Keyword.MYRIAD)

    triggeredAbility {
        trigger = Triggers.self.leaves()
        effect = Effects.CreateEldraziSpawn(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "63"
        artist = "Jehan Choo"
        imageUri = "https://cards.scryfall.io/normal/front/e/c/ec5fe7d0-b444-4488-b058-f3916e404cac.jpg?1783911420"
    }
}

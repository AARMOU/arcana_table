package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Rarity

/** Every squad copy has the same enters trigger and draws an additional card. */
val SicarianInfiltrator = card("Sicarian Infiltrator") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Artifact Creature — Human Soldier"
    power = 1
    toughness = 2
    oracleText = "Flash\nSquad {2}\nBenediction of the Omnissiah — When this creature enters, draw a card."
    keywords(Keyword.FLASH)
    squad("{2}")

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "25"
        artist = "Games Workshop"
    }
}

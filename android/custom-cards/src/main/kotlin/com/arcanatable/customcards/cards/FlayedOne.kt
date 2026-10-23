package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

val FlayedOne = card("Flayed One") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 4
    toughness = 1
    oracleText = "Lifelink\nFlesh Flayer — When this creature enters, mill three cards."
    keywords(Keyword.LIFELINK)

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Patterns.Library.mill(3)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "34"
        artist = "David Sondered"
    }
}

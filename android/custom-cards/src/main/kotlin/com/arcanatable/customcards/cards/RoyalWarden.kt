package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

val RoyalWarden = card("Royal Warden") {
    manaCost = "{3}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 3
    toughness = 2
    oracleText = "Phalanx Commander — When this creature enters, create two tapped 2/2 black Necron Warrior " +
        "artifact creature tokens.\nUnearth {3}{B} ({3}{B}: Return this card from your graveyard to the battlefield. " +
        "It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. " +
        "Unearth only as a sorcery.)"

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Necron", "Warrior"),
            name = "Necron Warrior",
            count = 2,
            tapped = true,
            artifactToken = true,
        )
    }
    unearth("{3}{B}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "52"
        artist = "Artur Nakhodkin"
    }
}

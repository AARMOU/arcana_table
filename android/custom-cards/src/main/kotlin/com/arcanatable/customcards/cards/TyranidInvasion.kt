package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player

/** Tyranid Invasion — Warhammer 40,000 Commander #102. */
val TyranidInvasion = card("Tyranid Invasion") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Create a number of 3/3 green Tyranid Warrior creature tokens with trample " +
        "equal to the number of opponents you have."

    spell {
        effect = Effects.CreateToken(
            count = DynamicAmounts.playerCount(Player.EachOpponent),
            power = 3,
            toughness = 3,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Tyranid", "Warrior"),
            keywords = setOf(Keyword.TRAMPLE),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "102"
        artist = "Games Workshop"
    }
}

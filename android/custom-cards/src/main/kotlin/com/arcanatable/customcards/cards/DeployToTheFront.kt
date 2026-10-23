package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player

/** Warhammer 40,000 Commander #184; count *every* player's creatures at resolution. */
val DeployToTheFront = card("Deploy to the Front") {
    manaCost = "{5}{W}{W}"
    colorIdentity = "W"
    typeLine = "Sorcery"
    oracleText = "Create X 1/1 white Soldier creature tokens, where X is the number of creatures on the battlefield."

    spell {
        effect = Effects.CreateToken(
            count = DynamicAmounts.battlefield(Player.Each, GameObjectFilter.Creature).count(),
            power = 1,
            toughness = 1,
            colors = setOf(Color.WHITE),
            creatureTypes = setOf("Soldier"),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "184"
        artist = "Aaron J. Riley"
    }
}

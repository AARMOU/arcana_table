package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val Silence = card("Silence") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Your opponents can't cast spells this turn."

    spell {
        effect = Effects.CantCastSpells(
            target = EffectTarget.PlayerRef(Player.EachOpponent),
            duration = Duration.EndOfTurn,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "35"
        imageUri = "https://cards.scryfall.io/normal/front/1/c/1c2b13b1-31f0-4676-88a7-53f3a190e9a2.jpg?1783939940"
    }
}

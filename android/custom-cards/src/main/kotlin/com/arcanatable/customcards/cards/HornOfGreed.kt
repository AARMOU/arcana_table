package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val HornOfGreed = card("Horn of Greed") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Whenever a player plays a land, that player draws a card."

    triggeredAbility {
        trigger = Triggers.anyPlayer.playsLand()
        effect = Effects.DrawCards(1, EffectTarget.PlayerRef(Player.TriggeringPlayer))
        description = "Whenever a player plays a land, that player draws a card."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "211"
        imageUri = "https://cards.scryfall.io/normal/front/1/4/14026e22-391c-4a57-a305-665e6066e847.jpg?1783937282"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val RiseOfTheEldrazi = card("Rise of the Eldrazi") {
    manaCost = "{8}{C}{C}{C}"
    colorIdentity = ""
    typeLine = "Sorcery"
    cantBeCountered = true
    oracleText = "This spell can't be countered.\nDestroy all creatures. They can't be regenerated.\nDraw three cards."

    spell {
        effect = Effects.DestroyAll(GameObjectFilter.Creature, noRegenerate = true) then
            Effects.DrawCards(3)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "1"
        artist = "Jason Felix"
    }
}

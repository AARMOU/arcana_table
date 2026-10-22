package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.references.Player

val SautekhImmortal = card("Sautekh Immortal") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 2
    toughness = 2
    oracleText = "Flash\nElite Troops — This creature enters with a +1/+1 counter on it for each creature that died this turn."

    keywords(Keyword.FLASH)
    replacementEffect(
        EntersWithDynamicCounters(count = DynamicAmounts.creaturesDiedThisTurn(Player.Each)),
    )

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "54"
        artist = "Games Workshop"
    }
}

package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
val TheirNameIsDeath = card("Their Name Is Death") {
    manaCost = "{3}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Destroy all nonartifact creatures."
    spell { effect = Effects.DestroyAll(GameObjectFilter.Creature.nonartifact()) }
    metadata { rarity = Rarity.RARE; collectorNumber = "62"; artist = "Evan Shipard" }
}

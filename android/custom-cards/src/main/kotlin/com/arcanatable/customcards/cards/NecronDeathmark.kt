package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Necron Deathmark — Warhammer 40,000 Commander #42. */
val NecronDeathmark = card("Necron Deathmark") {
    manaCost = "{3}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 5
    toughness = 3
    oracleText = "Flash\nSynaptic Disintegrator — When this creature enters, destroy up to " +
        "one target creature and target player mills three cards."

    keywords(Keyword.FLASH)
    triggeredAbility {
        trigger = Triggers.self.enters()
        val creature = target(TargetFilter.Creature, optional = true)
        val player = target(Targets.Player)
        effect = Effects.Destroy(creature) then Patterns.Library.mill(3, player)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "42"
        artist = "Games Workshop"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Bituminous Blast — Alara Reborn #34; reprinted in The Ruinous Powers. */
val BituminousBlast = card("Bituminous Blast") {
    manaCost = "{3}{B}{R}"
    colorIdentity = "BR"
    typeLine = "Instant"
    oracleText = "Cascade\nBituminous Blast deals 4 damage to target creature."

    keywords(Keyword.CASCADE)
    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.Cascade
        description = "Cascade"
    }
    spell {
        val creature = target(TargetFilter.Creature)
        effect = Effects.DealDamage(4, creature)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "34"
        artist = "Raymond Swanland"
        imageUri = "https://api.scryfall.com/cards/arb/34?format=image"
    }
}

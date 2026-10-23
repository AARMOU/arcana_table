package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Only creatures strictly weaker than the targeted friendly creature are destroyed. */
val MandateOfAbaddon = card("Mandate of Abaddon") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Choose target creature you control. Destroy all creatures with power less than that creature's power."

    spell {
        val benchmark = target(TargetFilter.CreatureYouControl)
        effect = Effects.DestroyAll(GameObjectFilter.Creature.powerLessThanEntity(benchmark))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "40"
        artist = "Games Workshop"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * The target is checked again at resolution. Only creatures whose then-current projected
 * power is strictly greater are destroyed; the chosen creature is never destroyed by
 * its own power comparison.
 */
val FellTheMighty = card("Fell the Mighty") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Sorcery"
    oracleText = "Destroy all creatures with power greater than target creature's power."

    spell {
        val benchmark = target(TargetFilter.Creature)
        effect = Effects.DestroyAll(
            GameObjectFilter.Creature.powerGreaterThanEntity(benchmark)
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "186"
        artist = "Raymond Swanland"
    }
}

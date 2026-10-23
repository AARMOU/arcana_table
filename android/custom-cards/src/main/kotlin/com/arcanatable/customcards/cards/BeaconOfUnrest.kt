package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Beacon of Unrest — Warhammer 40,000 Commander #194 (Necron Dynasties). */
val BeaconOfUnrest = card("Beacon of Unrest") {
    manaCost = "{3}{B}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Put target artifact or creature card from a graveyard onto the battlefield under " +
        "your control. Shuffle Beacon of Unrest into its owner's library."

    spell {
        val cardInGraveyard = target(TargetFilter(GameObjectFilter.CreatureOrArtifact, zone = Zone.GRAVEYARD))
        effect = Effects.Move(
            cardInGraveyard,
            Zone.BATTLEFIELD,
            controllerOverride = EffectTarget.Controller,
            fromZone = Zone.GRAVEYARD
        )
        selfShuffleIntoLibrary()
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "194"
        artist = "Rafater"
    }
}

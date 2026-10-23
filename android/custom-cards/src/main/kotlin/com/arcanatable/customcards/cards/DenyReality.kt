package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Deny Reality — Alara Reborn #19; reprinted in The Ruinous Powers. */
val DenyReality = card("Deny Reality") {
    manaCost = "{3}{U}{B}"
    colorIdentity = "UB"
    typeLine = "Sorcery"
    oracleText = "Cascade\nReturn target permanent to its owner's hand."

    keywords(Keyword.CASCADE)
    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.Cascade
        description = "Cascade"
    }
    spell {
        val permanent = target(TargetFilter(GameObjectFilter.Permanent))
        effect = Effects.ReturnToHand(permanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "19"
        artist = "Jean-Sebastien Rossbach"
        imageUri = "https://api.scryfall.com/cards/arb/19?format=image"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Only entering from a graveyard creates the optional nonland-exile trigger. */
val CanoptekTombSentinel = card("Canoptek Tomb Sentinel") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Insect"
    power = 4
    toughness = 3
    oracleText = "Vigilance\nExile Cannon — When this creature enters from a graveyard, " +
        "exile up to one target nonland permanent.\nUnearth {7}"

    keywords(Keyword.VIGILANCE)
    unearth("{7}")

    triggeredAbility {
        trigger = Triggers.self.enters(from = Zone.GRAVEYARD)
        val victim = target(TargetFilter(GameObjectFilter.NonlandPermanent), optional = true)
        effect = Effects.Exile(victim)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "152"
        artist = "Ivan Dedov"
    }
}

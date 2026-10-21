package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Blight Grenade — Warhammer 40,000 Commander #31. */
val BlightGrenade = card("Blight Grenade") {
    manaCost = "{4}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Destroy target creature.\nAll creatures get -3/-3 until end of turn."

    spell {
        val creature = target(TargetFilter.Creature)
        effect = Effects.Destroy(creature) then
            Effects.ForEachInGroup(
                GroupFilter(GameObjectFilter.Creature),
                Effects.ModifyStats(-3, -3, EffectTarget.IterationEntity),
            )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "31"
        artist = "Alexey Kruglov"
        imageUri = "https://api.scryfall.com/cards/40k/31?format=image"
    }
}

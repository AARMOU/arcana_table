package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator

val BileBlight = card("Bile Blight") {
    manaCost = "{B}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Target creature and all other creatures with the same name as that creature get -3/-3 until end of turn."

    spell {
        val creature = target(TargetFilter.Creature)
        // Select the whole group before applying the reduction; only the declared
        // creature is a target, so same-named hexproof creatures are affected too.
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.sharingNameWith(creature)),
            Effects.ModifyStats(-3, -3, EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "61"
        artist = "Vincent Proce"
    }
}

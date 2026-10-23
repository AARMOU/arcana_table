package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ChandrasIgnition = card("Chandra's Ignition") {
    manaCost = "{3}{R}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Target creature you control deals damage equal to its power to each other creature and each opponent."

    spell {
        val creature = target(TargetFilter.CreatureYouControl)
        val damage = DynamicAmounts.targetPower(0)
        effect = Effects.ForEachInGroup(
            filter = GroupFilter(GameObjectFilter.Creature).otherThanTarget(),
            effect = Effects.DealDamage(
                amount = damage,
                target = EffectTarget.IterationEntity,
                damageSource = creature,
            ),
        ) then Effects.DealDamage(
            amount = damage,
            target = EffectTarget.PlayerRef(Player.EachOpponent),
            damageSource = creature,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "137"
        imageUri = "https://api.scryfall.com/cards/named?exact=Chandra%27s%20Ignition&format=image"
    }
}

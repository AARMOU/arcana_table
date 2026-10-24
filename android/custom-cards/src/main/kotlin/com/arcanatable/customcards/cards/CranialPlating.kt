package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val CranialPlating = card("Cranial Plating") {
    manaCost = "{2}"
    colorIdentity = "B"
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +1/+0 for each artifact you control.\n" +
        "{B}{B}: Attach this Equipment to target creature you control.\nEquip {1}"

    val artifacts = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact).count()
    staticAbility {
        ability = GrantDynamicStats(
            filter = Filters.EquippedCreature,
            powerBonus = artifacts,
            toughnessBonus = DynamicAmount.Fixed(0),
        )
    }

    activatedAbility {
        cost = Costs.Mana("{B}{B}")
        val creature = target(TargetFilter(GameObjectFilter.Creature.youControl()))
        effect = Effects.AttachTargetEquipmentToCreature(EffectTarget.Self, creature)
    }
    equipAbility("{1}")

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "236"
        artist = "L J Koh"
    }
}

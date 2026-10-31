package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val CollectiveEffort = card("Collective Effort") {
    manaCost = "{1}{W}{W}"
    colorIdentity = "W"
    typeLine = "Sorcery"
    oracleText = "Escalate—Tap an untapped creature you control. (Pay this cost for each mode chosen beyond the first.)\nChoose one or more —\n• Destroy target creature with power 4 or greater.\n• Destroy target enchantment.\n• Put a +1/+1 counter on each creature target player controls."

    spell {
        modal(
            chooseCount = 3,
            minChooseCount = 1,
            additionalCostPerExtraMode = CostAtom.TapPermanents(1, GameObjectFilter.Creature),
        ) {
            mode("Destroy target creature with power 4 or greater.") {
                val creature = target(TargetFilter(GameObjectFilter.Creature.powerAtLeast(4)))
                effect = Effects.Destroy(creature)
            }
            mode("Destroy target enchantment.") {
                val enchantment = target(TargetFilter.Enchantment)
                effect = Effects.Destroy(enchantment)
            }
            mode("Put a +1/+1 counter on each creature target player controls.") {
                val player = target(Targets.Player)
                effect = Effects.ForEachInGroup(
                    GroupFilter(GameObjectFilter.Creature.targetPlayerControls(player)),
                    Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.IterationEntity),
                )
            }
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "126"
        artist = "Jurijus Chitrovas"
        imageUri = "https://cards.scryfall.io/normal/front/d/9/d9138f86-34dc-46d2-99f4-18eb3b26c253.jpg?1783903250"
    }
}

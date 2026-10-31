package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

val ArmoryAutomaton = card("Armory Automaton") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct"
    power = 2
    toughness = 2
    oracleText = "Whenever this creature enters or attacks, you may attach any number of target Equipment to it. (Control of the Equipment doesn't change.)"

    triggeredAbility {
        trigger = Triggers.or(Triggers.self.enters(), Triggers.self.attacks())
        target = TargetObject(
            filter = TargetFilter(baseFilter = GameObjectFilter.Artifact.withSubtype(Subtype.EQUIPMENT)),
            unlimited = true,
        )
        effect = Effects.May(
            Effects.ForEachTarget(
                Effects.AttachTargetEquipmentToCreature(
                    equipmentTarget = EffectTarget.ContextTarget(0),
                    creatureTarget = EffectTarget.Self,
                ),
            ),
        )
        description = "Whenever this creature enters or attacks, you may attach any number of target Equipment to it."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "51"
        artist = "James Paick"
    }
}

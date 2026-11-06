package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

val NurglesRot = card("Nurgle's Rot") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature an opponent controls\nWhen enchanted creature dies, return this card to its owner's hand " +
        "and you create a 1/3 black Demon creature token named Plaguebearer of Nurgle."

    val creatureTarget = TargetObject(
        filter = TargetFilter(GameObjectFilter.Creature.opponentControls()),
    )
    auraTarget = creatureTarget
    auraCastTarget = creatureTarget

    triggeredAbility {
        trigger = Triggers.attached.dies()
        effect = Effects.Move(EffectTarget.Self, Zone.HAND) then Effects.CreateToken(
            power = 1,
            toughness = 3,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Demon"),
            name = "Plaguebearer of Nurgle",
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "45"
        artist = "Roman Tishenin"
    }
}

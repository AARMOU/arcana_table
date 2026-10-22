package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Death's Presence — Tyranid Swarm.
 * The amount is the dying creature's LAST-KNOWN power, not its current
 * (already nonexistent) battlefield power. Targets are chosen separately
 * for each death trigger, including simultaneous creature deaths.
 */
val DeathsPresence = card("Death's Presence") {
    manaCost = "{5}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "Whenever a creature you control dies, put X +1/+1 counters on target creature " +
        "you control, where X is the power of the creature that died."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl()).dies()
        val creature = target(TargetFilter(GameObjectFilter.Creature.youControl()))
        effect = Effects.AddDynamicCounters(
            CounterType.PLUS_ONE_PLUS_ONE,
            DynamicAmounts.triggeringPower(),
            creature,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "212"
        artist = "Josu Hernaiz"
    }
}

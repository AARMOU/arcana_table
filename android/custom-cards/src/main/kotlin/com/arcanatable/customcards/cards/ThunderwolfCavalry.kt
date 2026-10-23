package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Each other creature controlled by the trigger controller gets its own counter. */
val ThunderwolfCavalry = card("Thunderwolf Cavalry") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Astartes Warrior"
    power = 4
    toughness = 4
    oracleText = "First strike\nCrushing Teeth — Whenever this creature deals combat damage to a player, " +
        "put a +1/+1 counter on each other creature you control."
    keywords(Keyword.FIRST_STRIKE)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl(), excludeSelf = true),
            Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.IterationEntity)
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "16"
        artist = "Oleg Shekhovtsov"
    }
}

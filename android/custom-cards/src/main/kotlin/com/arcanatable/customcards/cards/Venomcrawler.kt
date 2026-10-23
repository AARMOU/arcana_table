package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.WardCost
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Venomcrawler — Warhammer 40,000 Commander #68. */
val Venomcrawler = card("Venomcrawler") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Demon"
    power = 2
    toughness = 2
    oracleText = "Lifelink\nDevourer of Souls — Whenever another creature dies, put a +1/+1 counter on this creature."

    keywords(Keyword.LIFELINK)
    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature).dies()
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "68"
        artist = "Ivan Dedov"
    }
}

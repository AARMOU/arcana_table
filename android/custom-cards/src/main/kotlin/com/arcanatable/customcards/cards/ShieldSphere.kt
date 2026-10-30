package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ShieldSphere = card("Shield Sphere") {
    manaCost = "{0}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Wall"
    power = 0
    toughness = 6
    oracleText = "Defender\nWhenever this creature blocks, put a -0/-1 counter on it."
    keywords(Keyword.DEFENDER)

    triggeredAbility {
        trigger = Triggers.self.blocks()
        effect = Effects.AddCounters(CounterType.MINUS_ZERO_MINUS_ONE, 1, EffectTarget.Self)
        description = "Whenever this creature blocks, put a -0/-1 counter on it."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "166"
        artist = "Alan Rabinowitz"
    }
}

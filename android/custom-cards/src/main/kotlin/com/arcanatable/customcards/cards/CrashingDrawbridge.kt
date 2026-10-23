package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Throne of Eldraine #217. The effect applies to creatures present at resolution. */
val CrashingDrawbridge = card("Crashing Drawbridge") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Wall"
    power = 0
    toughness = 4
    oracleText = "Defender\n{T}: Creatures you control gain haste until end of turn."

    keywords(Keyword.DEFENDER)

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.GrantKeyword(Keyword.HASTE, EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "217"
        artist = "Mark Behm"
    }
}

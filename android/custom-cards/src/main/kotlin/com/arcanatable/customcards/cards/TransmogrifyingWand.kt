package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val TransmogrifyingWand = card("Transmogrifying Wand") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Transmogrifying Wand enters the battlefield with three charge counters on it.\n" +
        "{1}, {T}, Remove a charge counter from Transmogrifying Wand: Destroy target creature. " +
        "Its controller creates a 2/4 white Ox creature token. Activate only as a sorcery."

    replacementEffect(EntersWithCounters(counterType = CounterType.CHARGE, count = 3, selfOnly = true))

    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{1}"),
            Costs.Tap,
            Costs.RemoveCounterFromSelf(CounterType.CHARGE, 1),
        )
        val creature = target(TargetFilter.Creature)
        effect = Effects.Destroy(creature) then Effects.CreateToken(
            power = 2,
            toughness = 4,
            colors = setOf(Color.WHITE),
            creatureTypes = setOf("Ox"),
            name = "Ox",
            controller = EffectTarget.TargetController,
        )
        timing = TimingRule.SorcerySpeed
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "981"
        artist = "Aaron Miller"
    }
}

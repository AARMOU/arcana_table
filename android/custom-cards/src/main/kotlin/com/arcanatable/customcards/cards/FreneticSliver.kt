package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val FreneticSliver = card("Frenetic Sliver") {
    manaCost = "{1}{U}{R}"
    colorIdentity = "UR"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "All Slivers have \"{0}: If this permanent is on the battlefield, flip a coin. If you win the flip, exile this permanent and return it to the battlefield under its owner's control at the beginning of the next end step. If you lose the flip, sacrifice it.\""

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliverAbility = ActivatedAbilityBuilder().apply {
        cost = Costs.Mana("{0}")
        effect = Effects.If(
            Conditions.SourceInZone(Zone.BATTLEFIELD),
            Effects.FlipCoin(
                wonEffect = Effects.Move(EffectTarget.Self, Zone.EXILE) then
                    Effects.CreateDelayedTrigger(
                        step = Step.END,
                        expiry = DelayedTriggerExpiry.Never,
                        effect = Effects.Move(EffectTarget.Self, Zone.BATTLEFIELD, fromZone = Zone.EXILE),
                    ),
                lostEffect = SacrificeSelfEffect,
            ),
        )
    }.build()
    staticAbility { ability = GrantActivatedAbility(sliverAbility, slivers) }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "157"
        imageUri = "https://cards.scryfall.io/normal/front/4/2/424b1d87-ff9d-4511-a196-b8ae8b5b5e1d.jpg?1783943134"
    }
}

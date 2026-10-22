package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator

val PortTown = card("Port Town") {
    colorIdentity = "UW"
    typeLine = "Land"
    oracleText = "As this land enters, you may reveal a Plains or Island card from your hand. If you don't, this land enters tapped.\n{T}: Add {W} or {U}."

    replacementEffect(OnEnterRun(Effects.MayRevealCardFromHand(
        GameObjectFilter.Any.withAnySubtype("Plains", "Island"),
        otherwise = Effects.Tap(EffectTarget.Self),
    )))
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.WHITE)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLUE)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "256"
        artist = "Arthur Yuan"
    }
}

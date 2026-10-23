package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ImmoralBargain = card("Immoral Bargain") {
    manaCost = "{1}{B}{G}"
    colorIdentity = "BG"
    typeLine = "Sorcery"
    oracleText = "As an additional cost to cast this spell, sacrifice X creatures.\nDestroy X target nonland permanents."

    additionalCost(Costs.additional.SacrificePermanents(GameObjectFilter.Creature, minCount = 0))
    spell {
        targets(TargetFilter.NonlandPermanent, exactly = DynamicAmounts.xValue())
        effect = Effects.ForEachTarget(Effects.Destroy(EffectTarget.ContextTarget(0)))
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "47"
        imageUri = "https://cards.scryfall.io/normal/front/f/c/fc01f092-80fb-4643-8ada-c2e5aeb68658.jpg?1783903851"
    }
}

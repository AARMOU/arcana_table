package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val CompySwarm = card("Compy Swarm") {
    manaCost = "{1}{B}{G}"
    colorIdentity = "BG"
    typeLine = "Creature — Dinosaur"
    power = 2
    toughness = 2
    oracleText = "At the beginning of your end step, if a creature died this turn, create a tapped token that's a copy of this creature."
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        interveningIf = Conditions.CreatureDiedThisTurn
        effect = Effects.CreateTokenCopyOfTarget(EffectTarget.Self, tapped = true)
        description = "At the beginning of your end step, if a creature died this turn, create a tapped token that's a copy of this creature."
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "9"
        imageUri = "https://cards.scryfall.io/normal/front/8/0/800871ee-7394-4a31-98a7-4b92860208a1.jpg?1783913638"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Inversion Behemoth — M3C #34. Your-turn beginning-of-combat trigger with any number (including
 * zero) of creature targets, any controller, each switched independently until end of turn.
 */
// CARDKIT-SCAFFOLD — unregistered: implemented by Claude, awaiting CI scenario evidence before registration.
val InversionBehemoth = card("Inversion Behemoth") {
    manaCost = "{2}{C}{C}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 2
    toughness = 9
    oracleText = "At the beginning of combat on your turn, switch the power and toughness of each of any number of target creatures until end of turn."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        targets(TargetFilter.Creature, unlimited = true)
        effect = Effects.ForEachTarget(Effects.SwitchPowerToughness(EffectTarget.ContextTarget(0)))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "34"
        artist = "Raph Lomotan"
        imageUri = "https://cards.scryfall.io/normal/front/7/7/77a5ca5d-a377-4ee4-8e72-5a6b60c67521.jpg?1783911428"
    }
}

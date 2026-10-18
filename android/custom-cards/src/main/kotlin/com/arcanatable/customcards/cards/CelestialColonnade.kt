package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val CelestialColonnade = card("Celestial Colonnade") {
    colorIdentity = "WU"
    typeLine = "Land"
    oracleText = "This land enters tapped.\n{T}: Add {W} or {U}.\n{3}{W}{U}: Until end of turn, this land becomes a " +
        "4/4 white and blue Elemental creature with flying and vigilance. It's still a land."

    replacementEffect(EntersTapped())

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

    activatedAbility {
        cost = Costs.Mana("{3}{W}{U}")
        effect = Effects.BecomeCreature(
            target = EffectTarget.Self,
            power = 4,
            toughness = 4,
            keywords = setOf(Keyword.FLYING, Keyword.VIGILANCE),
            creatureTypes = setOf("Elemental"),
            colors = setOf("WHITE", "BLUE"),
            duration = Duration.EndOfTurn,
        )
        description = "{3}{W}{U}: Until end of turn, this land becomes a 4/4 white and blue Elemental creature with " +
            "flying and vigilance. It's still a land."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "133"
        imageUri = "https://cards.scryfall.io/normal/front/f/6/f6929259-2903-4f6f-9b06-42048fd55c6a.jpg?1783942037"
        artist = "Eric Deschamps"
    }
}

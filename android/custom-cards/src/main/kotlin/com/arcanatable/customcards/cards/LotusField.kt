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

val LotusField = card("Lotus Field") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "Hexproof\nThis land enters tapped.\nWhen this land enters, sacrifice two untapped lands.\n" +
        "{T}: Add three mana of any one color."

    keywords(Keyword.HEXPROOF)
    replacementEffect(EntersTapped())

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.Sacrifice(GameObjectFilter.Land.untapped(), 2, EffectTarget.Controller)
        description = "When this land enters, sacrifice two untapped lands."
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddAnyColorMana(3)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add three mana of any one color."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "249"
        imageUri = "https://cards.scryfall.io/normal/front/0/e/0e013033-3995-4ba8-b0c3-0614c79aaaab.jpg?1783932935"
        artist = "John Avon"
    }
}

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

val BagEndBanquet = card("Bag End Banquet") {
    manaCost = "{6}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "When this artifact enters, create three Food tokens.\n{T}: Add {C} for each Food you control."

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateFood(3)
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact.withSubtype("Food")).count())
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C} for each Food you control."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "5"
        artist = "Dmitry Burmak"
    }
}

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

val ReflectingPool = card("Reflecting Pool") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add one mana of any type that a land you control could produce."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddManaOfColorLandsCouldProduce(LandControllerScope.YOU)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add one mana of any color that a land you control could produce."
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(Conditions.YouControl(GameObjectFilter.Land.couldProduceColorlessMana())),
        )
        description = "{T}: Add {C}. Activate only if a land you control could produce colorless mana."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "328"
        imageUri = "https://cards.scryfall.io/normal/front/7/b/7b633ac2-bf98-42bd-8c26-8737a7d8edc7.jpg?1783946597"
        artist = "Adam Paquette"
    }
}

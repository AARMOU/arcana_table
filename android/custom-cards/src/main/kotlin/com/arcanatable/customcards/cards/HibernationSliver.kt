package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val HibernationSliver = card("Hibernation Sliver") {
    manaCost = "{U}{B}"
    colorIdentity = "UB"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "All Slivers have \"Pay 2 life: Return this permanent to its owner's hand.\""

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbilityBuilder().apply {
                cost = Costs.PayLife(2)
                effect = Effects.ReturnToHand(EffectTarget.Self)
            }.build(),
            filter = slivers,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "925"
        imageUri = "https://cards.scryfall.io/normal/front/1/e/1e224cfb-5bc1-490c-ab1e-f5405dc2fa0b.jpg?1783915415"
    }
}

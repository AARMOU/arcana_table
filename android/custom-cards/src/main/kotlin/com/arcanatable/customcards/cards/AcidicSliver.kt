package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val AcidicSliver = card("Acidic Sliver") {
    manaCost = "{B}{R}"
    colorIdentity = "BR"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "All Slivers have \"{2}, Sacrifice this permanent: This permanent deals 2 damage to any target.\""

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbilityBuilder().apply {
                cost = Costs.Composite(Costs.Mana("{2}"), Costs.SacrificeSelf)
                target = AnyTarget()
                effect = Effects.DealDamage(2, EffectTarget.ContextTarget(0))
            }.build(),
            filter = slivers,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "206"
        imageUri = "https://cards.scryfall.io/normal/front/d/0/d0b3ecf1-5c00-4aa0-92fd-54829495a3cf.jpg?1783938452"
    }
}

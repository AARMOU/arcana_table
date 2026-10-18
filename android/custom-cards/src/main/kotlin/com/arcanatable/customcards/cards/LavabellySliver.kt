package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val LavabellySliver = card("Lavabelly Sliver") {
    manaCost = "{1}{R}{W}"
    colorIdentity = "RW"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "Sliver creatures you control have \"When this creature enters, it deals 1 damage to target player or planeswalker and you gain 1 life.\""

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantTriggeredAbility(
            ability = grantedTriggeredAbility {
                trigger = Triggers.self.enters()
                target = TargetPlayerOrPlaneswalker()
                effect = Effects.DealDamage(1, EffectTarget.ContextTarget(0)) then Effects.GainLife(1)
            },
            filter = sliversYouControl,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "927"
        imageUri = "https://cards.scryfall.io/normal/front/e/0/e0de3c85-0662-4374-bd21-65d3a34f2263.jpg?1783915416"
    }
}

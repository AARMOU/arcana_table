package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val FungusSliver = card("Fungus Sliver") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Fungus Sliver"
    power = 2
    toughness = 2
    oracleText = "All Sliver creatures have \"Whenever this creature is dealt damage, put a +1/+1 counter on it.\""

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantTriggeredAbility(
            ability = grantedTriggeredAbility {
                trigger = Triggers.self.isDealtDamage()
                effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
            },
            filter = slivers,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "203"
        imageUri = "https://cards.scryfall.io/normal/front/8/6/864a67e4-5deb-45d5-bd42-3078716e142b.jpg?1783927782"
    }
}

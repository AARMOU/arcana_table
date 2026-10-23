package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val CloudshredderSliver = card("Cloudshredder Sliver") {
    manaCost = "{R}{W}"
    colorIdentity = "RW"
    typeLine = "Creature — Sliver"
    power = 1
    toughness = 1
    oracleText = "Sliver creatures you control have flying and haste."

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility { ability = GrantKeyword(Keyword.FLYING, sliversYouControl) }
    staticAbility { ability = GrantKeyword(Keyword.HASTE, sliversYouControl) }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "919"
        imageUri = "https://cards.scryfall.io/normal/front/9/f/9f523765-7704-4faf-a72b-8a2c414bb179.jpg?1783915417"
    }
}

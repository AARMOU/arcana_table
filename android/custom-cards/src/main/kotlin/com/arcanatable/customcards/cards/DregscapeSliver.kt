package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val DregscapeSliver = card("Dregscape Sliver") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "Each Sliver creature card in your graveyard has unearth {2}. ({2}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)"

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantActivatedAbility(
            ability = unearthAbility(ManaCost.parse("{2}")),
            filter = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver")),
            recipientZone = Zone.GRAVEYARD,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "88"
        imageUri = "https://cards.scryfall.io/normal/front/4/a/4a835b7e-cd63-486d-8b58-2f443f686e32.jpg?1783933129"
    }
}

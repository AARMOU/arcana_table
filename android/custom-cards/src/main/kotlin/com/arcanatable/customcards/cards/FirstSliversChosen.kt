package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val FirstSliversChosen = card("First Sliver's Chosen") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Sliver"
    power = 3
    toughness = 3
    oracleText = "Sliver creatures you control have exalted. (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)"

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility { ability = GrantKeyword(Keyword.EXALTED, sliversYouControl) }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "9"
        imageUri = "https://cards.scryfall.io/normal/front/7/f/7f5c4368-4f2e-4f5f-8944-8b0d49ab3e38.jpg?1783933163"
    }
}

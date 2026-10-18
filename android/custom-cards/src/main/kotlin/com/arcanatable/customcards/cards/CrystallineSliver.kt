package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val CrystallineSliver = card("Crystalline Sliver") {
    manaCost = "{W}{U}"
    colorIdentity = "WU"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "All Slivers have shroud. (They can't be the targets of spells or abilities.)"

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility { ability = GrantKeyword(Keyword.SHROUD, slivers) }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "920"
        imageUri = "https://cards.scryfall.io/normal/front/a/0/a0c7f341-c61e-491d-850a-221c6a57ac64.jpg?1783915415"
    }
}

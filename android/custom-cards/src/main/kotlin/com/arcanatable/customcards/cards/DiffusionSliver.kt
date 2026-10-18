package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val DiffusionSliver = card("Diffusion Sliver") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Sliver"
    power = 1
    toughness = 1
    oracleText = "Whenever a Sliver creature you control becomes the target of a spell or ability an opponent controls, counter that spell or ability unless its controller pays {2}."

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility { ability = GrantWard(WardCost.Mana("{2}"), sliversYouControl) }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "845"
        imageUri = "https://cards.scryfall.io/normal/front/f/5/f5994259-8d16-4f12-b528-1821005f79af.jpg?1783915442"
    }
}

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

/** Jaheira, Friend of the Forest. "Choose a Background" only matters for deck building. */
val JaheiraFriendOfTheForest = card("Jaheira, Friend of the Forest") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Human Elf Druid"
    power = 2
    toughness = 3
    oracleText = "Tokens you control have \"{T}: Add {G}.\"\nChoose a Background " +
        "(You can have a Background as a second commander.)"

    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbility(
                id = AbilityId.next(),
                cost = Costs.Tap,
                effect = Effects.AddMana(Color.GREEN),
                isManaAbility = true,
                timing = TimingRule.ManaAbility,
            ),
            filter = GroupFilter(GameObjectFilter.Permanent.token().youControl()),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "237"
        imageUri = "https://cards.scryfall.io/normal/front/f/e/feb7ad1b-4466-48f7-b46d-cc83d4e22b51.jpg?1783922710"
        artist = "Bryan Sola"
    }
}

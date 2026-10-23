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

/**
 * Rocco, Cabaretti Caterer — taken from upstream Argentum (main, 2026-10) where it has since been added;
 * it only uses primitives that exist in Arcana's pinned engine.
 */
val RoccoCabarettiCaterer = card("Rocco, Cabaretti Caterer") {
    manaCost = "{X}{R}{G}{W}"
    colorIdentity = "RGW"
    typeLine = "Legendary Creature — Elf Druid"
    power = 3
    toughness = 1
    oracleText = "When Rocco enters, if you cast it, you may search your library for a creature card " +
        "with mana value X or less, put it onto the battlefield, then shuffle."

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.WasCast
        effect = Effects.May(
            Patterns.Library.searchLibrary(
                filter = GameObjectFilter.Creature.manaValueAtMostDynamic(DynamicAmounts.castX()),
                count = 1,
                destination = SearchDestination.BATTLEFIELD,
                shuffleAfter = true
            )
        )
        description = "When Rocco enters, if you cast it, you may search your library for a creature " +
            "card with mana value X or less, put it onto the battlefield, then shuffle."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "218"
        artist = "Chris Seaman"
        imageUri = "https://cards.scryfall.io/normal/front/b/6/b6cf8b35-2a81-40fd-b383-becb81bef806.jpg?1783923072"
        ruling("2022-04-29", "If an effect allows you to cast Rocco without paying its mana cost, X is 0.")
    }
}

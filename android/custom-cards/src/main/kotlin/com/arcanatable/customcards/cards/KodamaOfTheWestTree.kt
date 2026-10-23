package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.predicates.StatePredicate
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

private val modifiedCreaturesYouControl = GameObjectFilter.Creature.youControl().let {
    it.copy(statePredicates = it.statePredicates + StatePredicate.IsModified)
}

val KodamaOfTheWestTree = card("Kodama of the West Tree") {
    manaCost = "{2}{G}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Spirit"
    power = 3
    toughness = 3
    oracleText = "Reach\n" +
        "Modified creatures you control have trample. (Equipment, Auras you control, and counters are modifications.)\n" +
        "Whenever a modified creature you control deals combat damage to a player, search your library for a basic land card, put it onto the battlefield tapped, then shuffle."

    keywords(Keyword.REACH)

    staticAbility {
        ability = GrantKeyword(Keyword.TRAMPLE, GroupFilter(modifiedCreaturesYouControl))
    }

    triggeredAbility {
        trigger = Triggers.a(modifiedCreaturesYouControl).dealsCombatDamage(Recipient.AnyPlayer)
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand,
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true,
        )
        description = "Whenever a modified creature you control deals combat damage to a player, search your library for a basic land card, put it onto the battlefield tapped, then shuffle."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "199"
        artist = "Daarken"
        imageUri = "https://cards.scryfall.io/normal/front/e/f/ef1e1dff-b559-441d-8df3-b6a418066aca.jpg?1783923845"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.evolve
import com.wingedsheep.sdk.dsl.evolveTriggeredAbility
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Tyranid Prime — Warhammer 40,000 Commander #145.
 * Grants the *actual* evolve triggered ability, not merely the visual keyword,
 * to each other creature you control.
 */
private val otherCreaturesForTyranidPrime = GroupFilter(
    GameObjectFilter.Creature.youControl(),
    excludeSelf = true,
)

val TyranidPrime = card("Tyranid Prime") {
    manaCost = "{1}{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Tyranid"
    power = 0
    toughness = 4
    oracleText = "Evolve\nSynapse Creature — Other creatures you control have evolve."

    evolve()

    staticAbility {
        ability = GrantKeyword(Keyword.EVOLVE, otherCreaturesForTyranidPrime)
    }
    staticAbility {
        ability = GrantTriggeredAbility(evolveTriggeredAbility(), otherCreaturesForTyranidPrime)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "145"
        artist = "Mathias Kollros"
    }
}

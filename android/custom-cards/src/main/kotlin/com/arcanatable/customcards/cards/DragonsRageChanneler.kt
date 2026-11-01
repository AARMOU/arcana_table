package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*

val DragonsRageChanneler = card("Dragon's Rage Channeler") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Creature — Human Shaman"
    power = 1
    toughness = 1
    oracleText = "Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)\nDelirium — As long as there are four or more card types among cards in your graveyard, this creature gets +2/+2, has flying, and attacks each combat if able."

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Noncreature)
        effect = Patterns.Library.surveil(1)
    }

    staticAbility {
        ability = ModifyStats(2, 2, Filters.Self)
        condition = Conditions.Delirium(4)
    }

    staticAbility {
        ability = GrantKeyword(Keyword.FLYING, Filters.Self)
        condition = Conditions.Delirium(4)
    }

    staticAbility {
        ability = MustAttack()
        condition = Conditions.Delirium(4)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "121"
        artist = "Martina Fačková"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val DragonsRageChanneler = card("Dragon's Rage Channeler") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Creature — Human Shaman"
    power = 1
    toughness = 1
    oracleText = "Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)\nDelirium — As long as there are four or more card types among cards in your graveyard, this creature gets +2/+2, has flying, and attacks each combat if able."

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Noncreature)
        effect = Effects.Surveil(1)
    }

    staticAbility {
        condition = Conditions.Delirium(4)
        ability = ModifyStats(2, 2, GroupFilter.source())
    }
    staticAbility {
        condition = Conditions.Delirium(4)
        ability = GrantKeyword(Keyword.FLYING, GroupFilter.source())
    }
    staticAbility {
        condition = Conditions.Delirium(4)
        ability = MustAttack()
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "121"
        artist = "Martina Fačková"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/** "Commander creatures" means creatures tagged as any player's commander, under your control. */
val BastionProtector = card("Bastion Protector") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Soldier"
    power = 3
    toughness = 3
    oracleText = "Commander creatures you control get +2/+2 and have indestructible."

    val commandersYouControl = GroupFilter(GameObjectFilter.Creature.commander().youControl())
    staticAbility {
        ability = ModifyStats(2, 2, commandersYouControl)
    }
    staticAbility {
        ability = GrantKeyword(Keyword.INDESTRUCTIBLE, commandersYouControl)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "182"
        artist = "Steve Argyle"
    }
}

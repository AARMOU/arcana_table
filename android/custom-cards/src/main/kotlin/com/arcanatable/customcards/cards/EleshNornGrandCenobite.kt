package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

private val otherCreaturesYouControl = GroupFilter(
    GameObjectFilter.Creature.youControl(),
    excludeSelf = true,
)

private val creaturesYourOpponentsControl = GroupFilter(GameObjectFilter.Creature.opponentControls())

val EleshNornGrandCenobite = card("Elesh Norn, Grand Cenobite") {
    manaCost = "{5}{W}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Phyrexian Praetor"
    power = 4
    toughness = 7
    oracleText = "Vigilance\nOther creatures you control get +2/+2.\nCreatures your opponents control get -2/-2."

    keywords(Keyword.VIGILANCE)

    staticAbility {
        ability = ModifyStats(powerBonus = 2, toughnessBonus = 2, filter = otherCreaturesYouControl)
    }
    staticAbility {
        ability = ModifyStats(powerBonus = -2, toughnessBonus = -2, filter = creaturesYourOpponentsControl)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "9"
        artist = "Igor Kieryluk"
        imageUri = "https://cards.scryfall.io/normal/front/b/6/b66390d6-1649-4bfa-92d3-77664650d552.jpg?1783941327"
    }
}

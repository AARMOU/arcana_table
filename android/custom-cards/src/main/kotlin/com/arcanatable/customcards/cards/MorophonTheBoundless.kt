package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.model.Rarity

/**
 * Morophon, the Boundless — CMM #3.
 */
val MorophonTheBoundless = card("Morophon, the Boundless") {
    manaCost = "{7}"
    colorIdentity = "WUBRG"
    typeLine = "Legendary Creature — Shapeshifter"
    power = 6
    toughness = 6
    oracleText = "Changeling (This card is every creature type.)\nAs Morophon enters, choose a creature type.\nSpells of the chosen type you cast cost {W}{U}{B}{R}{G} less to cast. This effect reduces only the amount of colored mana you pay.\nOther creatures you control of the chosen type get +1/+1."

    keywords(Keyword.CHANGELING)

    replacementEffect(EntersWithChoice(choiceType = ChoiceType.CREATURE_TYPE))

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Any.withChosenSubtype()),
            modification = CostModification.ReduceColored("{W}{U}{B}{R}{G}"),
        )
    }

    staticAbility {
        ability = ModifyStats(
            powerBonus = 1,
            toughnessBonus = 1,
            filter = GroupFilter(GameObjectFilter.Creature.youControl(), excludeSelf = true, chosenSubtypeKey = "chosenCreatureType"),
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "3"
        artist = "Victor Adame Minguez"
        imageUri = "https://cards.scryfall.io/normal/front/8/4/84238335-e08c-421c-b9b9-70a679ff2967.jpg?1783916893"
    }
}

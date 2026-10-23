package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val SpringleafParade = card("Springleaf Parade") {
    manaCost = "{X}{G}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "When this enchantment enters, create X 1/1 colorless Shapeshifter creature tokens with changeling.\n" +
        "Creature tokens you control have \"{T}: Add one mana of any color.\""

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            creatureTypes = setOf("Shapeshifter"),
            keywords = setOf(Keyword.CHANGELING),
            count = DynamicAmounts.castX(),
        )
        description = "When this enchantment enters, create X 1/1 colorless Shapeshifter creature tokens with changeling."
    }
    staticAbility {
        ability = GrantActivatedAbility(
            ability = grantedActivatedAbility {
                cost = Costs.Tap
                effect = Effects.AddManaOfChoice()
                manaAbility = true
                description = "{T}: Add one mana of any color."
            },
            filter = GroupFilter(GameObjectFilter.Creature.token().youControl()),
        )
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "19"
        imageUri = "https://cards.scryfall.io/normal/front/4/4/44265489-645a-43b0-bc1f-726905b06876.jpg?1783904601"
    }
}

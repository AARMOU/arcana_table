package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

val AcornHarvest = card("Acorn Harvest") {
    manaCost = "{3}{G}"
    colorIdentity = "BG"
    typeLine = "Sorcery"
    oracleText = "Create two 1/1 green Squirrel creature tokens.\nFlashback—{1}{B}{G}, Pay 3 life."
    spell { effect = Effects.CreateToken(1, 1, setOf(Color.GREEN), setOf("Squirrel"), count = 2) }
    keywordAbility(KeywordAbility.flashback("{1}{B}{G}", Costs.additional.PayLife(3)))
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "120"
        imageUri = "https://cards.scryfall.io/normal/front/3/2/32987720-cc0c-416b-b79b-217d3b37542d.jpg?1783945144"
    }
}

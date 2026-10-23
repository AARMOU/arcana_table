package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/** Bloodcrusher of Khorne — Warhammer 40,000 Commander #72. */
val BloodcrusherOfKhorne = card("Bloodcrusher of Khorne") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Demon Knight"
    power = 3
    toughness = 3
    oracleText = "Trample\nDevastating Charge — Other creatures you control have trample."

    keywords(Keyword.TRAMPLE)
    staticAbility {
        ability = GrantKeyword(
            Keyword.TRAMPLE,
            GroupFilter(GameObjectFilter.Creature.youControl(), excludeSelf = true),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "72"
        artist = "Games Workshop"
    }
}

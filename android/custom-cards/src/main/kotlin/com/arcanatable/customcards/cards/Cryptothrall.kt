package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val Cryptothrall = card("Cryptothrall") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct"
    power = 3
    toughness = 3
    oracleText = "Protector — Other artifact creatures you control have hexproof."

    staticAbility {
        ability = GrantKeyword(
            Keyword.HEXPROOF,
            GroupFilter(GameObjectFilter.ArtifactCreature.youControl(), excludeSelf = true),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "155"
        artist = "Henry Peters"
    }
}

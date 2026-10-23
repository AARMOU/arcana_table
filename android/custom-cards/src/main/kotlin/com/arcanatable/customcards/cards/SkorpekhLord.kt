package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val SkorpekhLord = card("Skorpekh Lord") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron Noble"
    power = 3
    toughness = 2
    oracleText = "Menace\nCommand Protocols — Other artifact creatures you control get +1/+0 and have menace.\nUnearth {2}{B}"

    keywords(Keyword.MENACE)
    val otherArtifactCreatures = GroupFilter(GameObjectFilter.ArtifactCreature.youControl(), excludeSelf = true)
    staticAbility { ability = ModifyStats(1, 0, otherArtifactCreatures) }
    staticAbility { ability = GrantKeyword(Keyword.MENACE, otherArtifactCreatures) }
    unearth("{2}{B}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "58"
        artist = "Adrián Rodríguez Pérez"
    }
}

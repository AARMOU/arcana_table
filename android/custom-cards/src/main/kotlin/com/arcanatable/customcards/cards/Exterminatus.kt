package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator

val Exterminatus = card("Exterminatus") {
    manaCost = "{5}{W}{B}"
    colorIdentity = "BW"
    typeLine = "Sorcery"
    oracleText = "Nonland permanents your opponents control lose indestructible until end of turn.\nDestroy all nonland permanents."

    spell {
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.NonlandPermanent.opponentControls()),
            Effects.RemoveKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity),
        ) then Effects.DestroyAll(GameObjectFilter.NonlandPermanent)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "120"
        artist = "Games Workshop"
    }
}

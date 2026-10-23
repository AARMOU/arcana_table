package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator

val AkromasWill = card("Akroma's Will") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Choose one. If you control a commander as you cast this spell, you may choose both instead.\n• Creatures you control gain flying, vigilance, and double strike until end of turn.\n• Creatures you control gain lifelink, indestructible, and protection from each color until end of turn."

    spell {
        val creatures = GroupFilter(GameObjectFilter.Creature.youControl())
        val firstMode = Effects.ForEachInGroup(
            creatures,
            Effects.GrantKeyword(Keyword.FLYING, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantKeyword(Keyword.VIGILANCE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantKeyword(Keyword.DOUBLE_STRIKE, EffectTarget.IterationEntity, Duration.EndOfTurn),
        )
        val secondMode = Effects.ForEachInGroup(
            creatures,
            Effects.GrantKeyword(Keyword.LIFELINK, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantProtectionFromColor(Color.WHITE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantProtectionFromColor(Color.BLUE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantProtectionFromColor(Color.BLACK, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantProtectionFromColor(Color.RED, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantProtectionFromColor(Color.GREEN, EffectTarget.IterationEntity, Duration.EndOfTurn),
        )
        effect = Effects.Modal(
            modes = listOf(
                Mode(effect = firstMode, description = "Flying, vigilance, and double strike"),
                Mode(effect = secondMode, description = "Lifelink, indestructible, and protection from each color"),
            ),
            chooseCount = 2,
            minChooseCount = 1,
            dynamicChooseCount = DynamicAmounts.conditional(
                Conditions.CompareAmounts(
                    DynamicAmounts.battlefield(Player.You, GameObjectFilter.Any.commander()).count(),
                    ComparisonOperator.GTE,
                    1,
                ),
                2,
                1,
            ),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "3"
        imageUri = "https://api.scryfall.com/cards/named?exact=Akroma%27s%20Will&format=image"
    }
}

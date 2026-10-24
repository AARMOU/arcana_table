package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Fixed-color grants, with no choice and no protection from colorless. */
private fun immunityFromEachColor(target: EffectTarget) =
    Effects.GrantProtectionFromColor(Color.WHITE, target, Duration.EndOfTurn) then
        Effects.GrantProtectionFromColor(Color.BLUE, target, Duration.EndOfTurn) then
        Effects.GrantProtectionFromColor(Color.BLACK, target, Duration.EndOfTurn) then
        Effects.GrantProtectionFromColor(Color.RED, target, Duration.EndOfTurn) then
        Effects.GrantProtectionFromColor(Color.GREEN, target, Duration.EndOfTurn)

// CARDKIT-SCAFFOLD: unregistered candidate; real scenario CI must pass before registration.
val EldritchImmunity = card("Eldritch Immunity") {
    manaCost = "{C}"
    colorIdentity = ""
    typeLine = "Kindred Instant — Eldrazi"
    oracleText = "Target creature you control gains protection from each color until end of turn.\nOverload {4}{C} (You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\")"

    keywordAbility(KeywordAbility.overload("{4}{C}"))

    spell {
        val creature = target(TargetFilter.CreatureYouControl)
        effect = immunityFromEachColor(creature)
        // Overload has no targets: shroud does not stop it affecting our creatures.
        overloadEffect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            immunityFromEachColor(EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "33"
        artist = "Carlos Palma Cruchaga"
        imageUri = "https://cards.scryfall.io/normal/front/6/4/64a63b90-dbd6-4b66-8031-a3e230ada5b9.jpg?1783911428"
    }
}

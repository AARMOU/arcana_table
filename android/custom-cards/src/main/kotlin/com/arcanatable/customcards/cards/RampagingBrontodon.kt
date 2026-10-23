package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.Duration

val RampagingBrontodon = card("Rampaging Brontodon") {
    manaCost = "{5}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur"
    power = 7
    toughness = 7
    oracleText = "Trample\nWhenever this creature attacks, it gets +1/+1 until end of turn for each land you control."
    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.self.attacks()
        val landCount = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).count()
        effect = Effects.ModifyStats(landCount, landCount, EffectTarget.Self, Duration.EndOfTurn)
        description = "Whenever this creature attacks, it gets +1/+1 until end of turn for each land you control."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "247"
        artist = "Lars Grant-West"
        imageUri = "https://cards.scryfall.io/normal/front/e/a/ea3f71d6-e333-4e50-9545-de73749413c8.jpg?1783913858"
    }
}

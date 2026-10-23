package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val CaptainJamesTKirk = card("Captain James T. Kirk") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Human Officer"
    power = 2
    toughness = 2
    oracleText = "Whenever Captain Kirk enters or attacks, choose one. If you have no cards in hand, choose one or more instead.\n" +
        "• Discard a card, then draw a card.\n" +
        "• Create a 1/1 red Officer creature token.\n" +
        "• Creatures you control get +1/+0 until end of turn."

    triggeredAbility {
        trigger = Triggers.or(Triggers.self.enters(), Triggers.self.attacks())
        effect = Effects.Modal(
            modes = listOf(
                Mode(
                    effect = Effects.Discard(1) then Effects.DrawCards(1),
                    description = "Discard a card, then draw a card",
                ),
                Mode(
                    effect = Effects.CreateToken(
                        power = 1,
                        toughness = 1,
                        colors = setOf(Color.RED),
                        creatureTypes = setOf("Officer"),
                    ),
                    description = "Create a 1/1 red Officer creature token",
                ),
                Mode(
                    effect = Patterns.Group.modifyStatsForAll(1, 0, GroupFilter.AllCreaturesYouControl),
                    description = "Creatures you control get +1/+0 until end of turn",
                ),
            ),
            chooseCount = 3,
            minChooseCount = 1,
            dynamicChooseCount = DynamicAmounts.conditional(Conditions.EmptyHand, 3, 1),
        )
        description = "Whenever Captain Kirk enters or attacks, choose one. If you have no cards in hand, choose one or more instead."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "142"
        artist = "Chris Rallis"
        imageUri = "https://api.scryfall.com/cards/trk/142?format=image&version=normal"
    }
}

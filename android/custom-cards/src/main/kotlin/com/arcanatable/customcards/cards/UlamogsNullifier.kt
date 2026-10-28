package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Rarity

/**
 * Ulamog's Nullifier — BFZ #207.
 */
val UlamogsNullifier = card("Ulamog's Nullifier") {
    manaCost = "{2}{U}{B}"
    colorIdentity = "UB"
    typeLine = "Creature — Eldrazi Processor"
    power = 2
    toughness = 3
    oracleText = "Devoid (This card has no color.)\nFlash\nFlying\nWhen this creature enters, you may put two cards your opponents own from exile into their owners' graveyards. If you do, counter target spell."

    keywords(Keyword.DEVOID, Keyword.FLASH, Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.enters()
        target(TargetFilter.SpellOnStack)
        effect = Effects.If(
            Conditions.CompareAmounts(
                DynamicAmounts.count(Player.EachOpponent, Zone.EXILE, GameObjectFilter.Any),
                ComparisonOperator.GTE,
                2,
            ),
            Effects.May(
                Effects.Pipeline {
                    val exiled = gather(CardSource.FromZone(Zone.EXILE, Player.EachOpponent))
                    val two = chooseExactly(2, exiled)
                    toGraveyard(two)
                    run(Effects.CounterSpell())
                },
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "207"
        artist = "Aleksi Briclot"
        imageUri = "https://cards.scryfall.io/normal/front/a/c/acd482ad-ca4a-470f-8a76-14ec7b58316a.jpg?1783938181"
    }
}

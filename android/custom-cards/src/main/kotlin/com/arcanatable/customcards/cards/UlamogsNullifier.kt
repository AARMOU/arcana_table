package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CardDestination
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
        // The ETB must target a spell, even if the controller declines processing.
        target(TargetFilter.SpellOnStack)
        effect = Effects.Pipeline {
            // Check actual ownership, not the controller of the effect that exiled a card.
            val eligible = gather(
                CardSource.FromZone(
                    zone = Zone.EXILE,
                    player = Player.Each,
                    filter = GameObjectFilter.Any.ownedByOpponent(),
                ),
                name = "nullifierEligibleOpponentOwnedExiles",
            )
            ifNotEmpty(eligible, minSize = 2) {
                run(Effects.May(Effects.Pipeline {
                    val chosen = chooseExactly(
                        2,
                        from = eligible,
                        prompt = "Choose two cards your opponents own from exile",
                    )
                    val moved = moveTracked(
                        chosen,
                        CardDestination.ToZone(Zone.GRAVEYARD),
                        name = "nullifierProcessedCards",
                    )
                    // "If you do": counter only after two cards actually moved.
                    ifNotEmpty(moved, minSize = 2) {
                        run(Effects.CounterSpell())
                    }
                }))
            }
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "207"
        artist = "Aleksi Briclot"
        imageUri = "https://cards.scryfall.io/normal/front/a/c/acd482ad-ca4a-470f-8a76-14ec7b58316a.jpg?1783938181"
    }
}

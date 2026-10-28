package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Ulamog's Nullifier — exact processor-choice candidate.
 *
 * The exile zone is partitioned by owner, not controller. Gather from every
 * player's exile zone and then filter by actual card ownership; the latter
 * matters when a card is exiled under another player's effects.
 *
 * CARDKIT-SCAFFOLD: unregistered, unfinished validation candidate.\n * Keep this source UNREGISTERED until executable Argentum tests prove the
 * entire processing/countering transaction and replacement interactions.
 */
val UlamogsNullifier = card("Ulamog's Nullifier") {
    manaCost = "{2}{U}{B}"
    colorIdentity = "UB"
    typeLine = "Creature — Eldrazi Processor"
    power = 2
    toughness = 3
    oracleText = "Devoid\nFlash\nFlying\n" +
        "When this creature enters, you may put two cards your opponents own from exile " +
        "into their owners' graveyards. If you do, counter target spell."

    keywords(Keyword.DEVOID, Keyword.FLASH, Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.enters()
        // The trigger targets a SPELL, never an ability, even if processing is declined.
        target(TargetFilter.SpellOnStack)
        effect = Effects.Pipeline {
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
                    // If processing did not move both cards, counter nothing.
                    ifNotEmpty(moved, minSize = 2) {
                        run(Effects.CounterSpell())
                    }
                }))
            }
        }
        description = "You may put two cards your opponents own from exile into " +
            "their owners' graveyards. If you do, counter target spell."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "275"
        artist = "Aleksi Briclot"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

val SireOfStagnation = card("Sire of Stagnation") {
    manaCost = "{4}{U}{B}"
    colorIdentity = "UB"
    typeLine = "Creature — Eldrazi"
    power = 5
    toughness = 7
    oracleText = "Devoid\nWhenever a land an opponent controls enters, that player exiles the top two cards of their " +
        "library and you draw two cards."

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Land.opponentControls()).enters()
        effect = Effects.Pipeline {
            // "That player" is the entering LAND'S controller, not necessarily its
            // owner or the player who caused the zone change. In particular,
            // Oblivion Sower can put lands owned by us under an opponent's control.
            val topTwo = gather(
                CardSource.TopOfLibrary(2, Player.ControllerOfTriggeringEntity),
                revealed = true,
                name = "sireOfStagnationTopTwo",
            )
            move(topTwo, CardDestination.ToZone(Zone.EXILE, Player.ControllerOfTriggeringEntity))
            run(Effects.DrawCards(2))
        }
        description = "Whenever a land an opponent controls enters, that player exiles the top two cards of their " +
            "library and you draw two cards."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "206"
        artist = "Mike Bierek"
    }
}

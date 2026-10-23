package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

val OblivionSower = card("Oblivion Sower") {
    manaCost = "{6}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 5
    toughness = 8
    oracleText = "When you cast this spell, target opponent exiles the top four cards of their library, " +
        "then you may put any number of land cards that player owns from exile onto the battlefield under your control."

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Creature.named("Oblivion Sower"))
        val opponent = target(Targets.Opponent)
        effect = Effects.Pipeline {
            val topFour = gather(CardSource.TopOfLibrary(4, Player.TargetPlayer), name = "oblivionSowerTopFour")
            move(topFour, CardDestination.ToZone(Zone.EXILE, Player.TargetPlayer))
            val lands = gather(
                CardSource.FromZone(
                    Zone.EXILE,
                    Player.Any,
                    GameObjectFilter.Land.ownedByTargetPlayer(),
                ),
                name = "oblivionSowerExiledLands",
            )
            val chosen = chooseAnyNumber(
                from = lands,
                filter = GameObjectFilter.Land.ownedByTargetPlayer(),
                prompt = "Choose any number of lands that opponent owns from exile",
            )
            move(chosen, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You))
        }
        description = "When you cast this spell, target opponent exiles the top four cards of their library, " +
            "then you may put any number of land cards that player owns from exile onto the battlefield under your control."
    }

    metadata { rarity = Rarity.MYTHIC; collectorNumber = "158"; artist = "Mathias Kollros" }
}

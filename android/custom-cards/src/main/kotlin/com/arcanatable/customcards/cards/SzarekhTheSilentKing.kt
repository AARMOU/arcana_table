package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val SzarekhTheSilentKing = card("Szarekh, the Silent King") {
    manaCost = "{1}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Artifact Creature — Necron"
    power = 3
    toughness = 4
    oracleText = "Flying\nMy Will Be Done — Whenever Szarekh attacks, mill three cards. " +
        "You may put an artifact creature card or Vehicle card from among the cards milled this way into your hand."

    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Pipeline {
            val milled = mill(3)
            val eligible = ((GameObjectFilter.Creature and GameObjectFilter.Artifact) or GameObjectFilter.Any.withSubtype("Vehicle"))
                .currentlyIn(Zone.GRAVEYARD)
            val returned = chooseUpTo(
                1,
                from = milled,
                filter = eligible,
                prompt = "Put an artifact creature or Vehicle milled this way into your hand",
            )
            toHand(returned)
        }
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "1"
        artist = "Anton Solovianchyk"
    }
}

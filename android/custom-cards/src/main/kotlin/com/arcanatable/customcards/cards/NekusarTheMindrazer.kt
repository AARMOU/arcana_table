package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val NekusarTheMindrazer = card("Nekusar, the Mindrazer") {
    manaCost = "{2}{U}{B}{R}"
    colorIdentity = "UBR"
    typeLine = "Legendary Creature — Zombie Wizard"
    power = 2
    toughness = 4
    oracleText = "At the beginning of each player's draw step, that player draws an additional card.\n" +
        "Whenever an opponent draws a card, Nekusar deals 1 damage to that player."

    triggeredAbility {
        trigger = Triggers.anyPlayer.beginningOf(Step.DRAW)
        effect = Effects.DrawCards(1, EffectTarget.PlayerRef(Player.TriggeringPlayer))
        description = "At the beginning of each player's draw step, that player draws an additional card."
    }

    triggeredAbility {
        trigger = Triggers.anOpponent.draws()
        effect = Effects.DealDamage(1, EffectTarget.PlayerRef(Player.TriggeringPlayer))
        description = "Whenever an opponent draws a card, Nekusar deals 1 damage to that player."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "349"
        imageUri = "https://cards.scryfall.io/normal/front/4/a/4afdc65d-3c97-47af-83fa-df340389802e.jpg?1783915612"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val HormagauntHorde = card("Hormagaunt Horde") {
    manaCost = "{X}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Tyranid"
    power = 1
    toughness = 1
    oracleText = "Ravenous (This creature enters with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters.)\nEndless Swarm — Whenever a land you control enters, you may pay {2}{G}. If you do, return this card from your graveyard to your hand."

    ravenous()

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Land.youControl()).enters()
        triggerZone = Zone.GRAVEYARD
        effect = Effects.MayPay(
            cost = ManaCost.parse("{2}{G}"),
            then = Effects.Move(EffectTarget.Self, Zone.HAND),
        )
        description = "Endless Swarm — Whenever a land you control enters, you may pay {2}{G}. If " +
            "you do, return this card from your graveyard to your hand."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "93"
        artist = "Nikola Matkovic"
        imageUri = "https://cards.scryfall.io/normal/front/6/a/6a623ab2-4cdf-4e2e-a14d-6a709cf34cf1.jpg?1783920876"
    }
}

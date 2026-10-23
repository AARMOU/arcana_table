package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val RampagingFerocidon = card("Rampaging Ferocidon") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Dinosaur"
    power = 3
    toughness = 3
    oracleText = "Menace\nPlayers can't gain life.\n" +
        "Whenever another creature enters, this creature deals 1 damage to that creature's controller."

    keywords(Keyword.MENACE)

    replacementEffect(
        PreventLifeGain(EventPattern.LifeGainEvent(Player.Each)),
    )

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature).enters()
        effect = Effects.DealDamage(
            1,
            EffectTarget.PlayerRef(Player.ControllerOfTriggeringEntity),
        )
        description = "Whenever another creature enters, this creature deals 1 damage to that creature's controller."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "167"
        artist = "Jonathan Kuo"
        imageUri = "https://cards.scryfall.io/normal/front/5/3/53c38f4e-81ec-45b3-a6cf-06a65b674978.jpg?1783909611"
    }
}

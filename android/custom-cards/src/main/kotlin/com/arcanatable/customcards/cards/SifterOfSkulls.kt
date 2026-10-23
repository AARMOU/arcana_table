package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/** Not tokens, not opposing creatures, and not this Sifter itself. */
val SifterOfSkulls = card("Sifter of Skulls") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Eldrazi"
    power = 4
    toughness = 3
    oracleText = "Devoid\nWhenever another nontoken creature you control dies, create a 1/1 colorless " +
        "Eldrazi Scion creature token. It has \"Sacrifice this token: Add {C}.\""

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.another(
            GameObjectFilter.Creature.youControl().withCardPredicate(CardPredicate.IsNontoken)
        ).dies()
        effect = createEldraziScions(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "203"
        artist = "Slawomir Maniak"
    }
}

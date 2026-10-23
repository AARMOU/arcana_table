package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** A Scion is sacrificed as an activation cost, before the tap ability resolves. */
val DrownerOfHope = card("Drowner of Hope") {
    manaCost = "{5}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Eldrazi"
    power = 5
    toughness = 5
    oracleText = "Devoid\nWhen this creature enters, create two 1/1 colorless Eldrazi Scion creature tokens. " +
        "They have \"Sacrifice this token: Add {C}.\"\nSacrifice an Eldrazi Scion: Tap target creature."

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = createEldraziScions(2)
    }

    activatedAbility {
        val creature = target(TargetFilter.Creature)
        cost = Costs.Sacrifice(GameObjectFilter.Creature.withSubtype("Eldrazi").withSubtype("Scion"))
        effect = Effects.Tap(creature)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "182"
        artist = "Nicholas Gregory"
    }
}

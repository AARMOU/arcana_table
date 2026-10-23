package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Violent Outburst — Alara Reborn #63. */
val ViolentOutburst = card("Violent Outburst") {
    manaCost = "{1}{G}{R}"
    colorIdentity = "GR"
    typeLine = "Instant"
    oracleText = "Cascade (When you cast this spell, exile cards from the top of your library " +
        "until you exile a nonland card that costs less. You may cast it without paying its mana " +
        "cost. Put the exiled cards on the bottom in a random order.)\n" +
        "Creatures you control get +1/+0 until end of turn."

    keywords(Keyword.CASCADE)

    spell {
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.ModifyStats(1, 0, EffectTarget.IterationEntity),
        )
    }

    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.Cascade
        description = "Cascade"
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "63"
        artist = "Richard Whitters"
        imageUri = "https://cards.scryfall.io/normal/front/1/b/1b58856a-f88e-4625-8636-62b5c717b956.jpg?1783942428"
    }
}

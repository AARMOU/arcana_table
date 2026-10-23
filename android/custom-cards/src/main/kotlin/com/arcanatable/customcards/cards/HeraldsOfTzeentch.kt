package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.WardCost
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Heralds of Tzeentch — Warhammer 40,000 Commander #23. */
val HeraldsOfTzeentch = card("Heralds of Tzeentch") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Demon"
    power = 3
    toughness = 3
    oracleText = "Flying\nCascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)"

    keywords(Keyword.FLYING, Keyword.CASCADE)
    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.Cascade
        description = "Cascade"
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "23"
        artist = "Games Workshop"
    }
}

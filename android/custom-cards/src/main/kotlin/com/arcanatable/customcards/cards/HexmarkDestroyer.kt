package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CantBeBlockedByFewerThan

/** Full six-blocker requirement and rules-accurate graveyard unearth. */
val HexmarkDestroyer = card("Hexmark Destroyer") {
    manaCost = "{4}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 6
    toughness = 6
    oracleText = "Multi-threat Eliminator — This creature can't be blocked except by six or more creatures.\n" +
        "Unearth {4}{B}{B} ({4}{B}{B}: Return this card from your graveyard to the battlefield. " +
        "It gains haste. Exile it at the beginning of the next end step or if it would leave " +
        "the battlefield. Unearth only as a sorcery.)"
    staticAbility { ability = CantBeBlockedByFewerThan(6) }
    unearth("{4}{B}{B}")
    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "36"; artist = "Wonchun Choi" }
}

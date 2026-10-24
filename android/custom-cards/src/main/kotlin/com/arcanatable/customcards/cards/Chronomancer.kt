package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val Chronomancer = card("Chronomancer") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron Wizard"
    power = 1
    toughness = 1
    oracleText = "Flying\nAtomic Transmutation — {1}, {T}, Sacrifice another artifact: Draw a card.\n" +
        "Unearth {2}{B} ({2}{B}: Return this card from your graveyard to the battlefield. It gains haste. " +
        "Exile it at the beginning of the next end step or if it would leave the battlefield. " +
        "Unearth only as a sorcery.)"

    keywords(Keyword.FLYING)
    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{1}"),
            Costs.Tap,
            Costs.SacrificeAnother(GameObjectFilter.Artifact.youControl()),
        )
        effect = Effects.DrawCards(1)
    }
    unearth("{2}{B}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "32"
        artist = "Alexey Kruglov"
    }
}

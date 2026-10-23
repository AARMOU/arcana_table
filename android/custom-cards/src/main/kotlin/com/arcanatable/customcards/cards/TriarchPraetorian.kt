package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Only actual graveyard -> battlefield ETBs draw; casting from a graveyard does not. */
val TriarchPraetorian = card("Triarch Praetorian") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 2
    toughness = 1
    oracleText = "Flying\nDynastic Codes — When this creature enters from a graveyard, " +
        "you draw two cards and you lose 2 life.\nUnearth {4}{B}"

    keywords(Keyword.FLYING)
    unearth("{4}{B}")

    triggeredAbility {
        trigger = Triggers.self.enters(from = Zone.GRAVEYARD)
        effect = Effects.DrawCards(2) then Effects.LoseLife(2, EffectTarget.Controller)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "66"
        artist = "Lucas Terryn"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate

val KeeperOfSecrets = card("Keeper of Secrets") {
    manaCost = "{5}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Demon"
    power = 6
    toughness = 4
    oracleText = "First strike, haste\nSymphony of Pain — Whenever you cast a spell from anywhere other than your hand, " +
        "Keeper of Secrets deals damage equal to that spell's mana value to target opponent."

    keywords(Keyword.FIRST_STRIKE, Keyword.HASTE)

    triggeredAbility {
        trigger = Triggers.you.casts(
            GameObjectFilter.Any,
            setOf(SpellCastPredicate.CastFromZoneOtherThan(Zone.HAND)),
        )
        val opponent = target(Targets.Opponent)
        effect = Effects.DealDamage(DynamicAmounts.triggeringSpellManaValue(), opponent)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "78"
        artist = "Miguel Sacristan"
    }
}

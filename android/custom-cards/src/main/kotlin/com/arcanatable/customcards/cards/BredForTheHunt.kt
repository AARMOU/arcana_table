package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

import com.wingedsheep.sdk.scripting.events.Recipient

val BredForTheHunt = card("Bred for the Hunt") {
    manaCost = "{1}{G}{U}"
    colorIdentity = "GU"
    typeLine = "Enchantment"
    oracleText = "Whenever a creature you control with a +1/+1 counter on it deals combat damage to a player, you may draw a card."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl().withCounter(CounterType.PLUS_ONE_PLUS_ONE))
            .dealsCombatDamage(Recipient.AnyPlayer)
        optional = true
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "222"
        artist = "David Astruga"
    }
}

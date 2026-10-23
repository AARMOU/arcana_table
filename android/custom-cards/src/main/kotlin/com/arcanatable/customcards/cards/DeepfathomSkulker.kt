package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val DeepfathomSkulker = card("Deepfathom Skulker") {
    manaCost = "{5}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Eldrazi"
    power = 4
    toughness = 4
    oracleText = "Devoid (This card has no color.)\nWhenever a creature you control deals combat damage to a player, you may draw a card.\n{3}{C}: Target creature can't be blocked this turn."
    keywords(Keyword.DEVOID)
    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl()).dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.May(Effects.DrawCards(1))
    }
    activatedAbility {
        val creature = target(TargetFilter.Creature)
        cost = Costs.Mana("{3}{C}")
        effect = Effects.GrantKeyword(AbilityFlag.CANT_BE_BLOCKED, creature)
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "43"; artist = "Joseph Meehan" }
}

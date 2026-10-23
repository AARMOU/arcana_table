package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * The combat-damaged player is the triggering player. Restrict the target to an
 * artifact or enchantment controlled by that player (not merely an opponent).
 */
val Aberrant = card("Aberrant") {
    manaCost = "{X}{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Tyranid Mutant"
    power = 0
    toughness = 0
    oracleText = "Ravenous\nTrample\nHeavy Power Hammer — Whenever this creature deals combat damage to a player, " +
        "destroy target artifact or enchantment that player controls."
    ravenous()
    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        val victim = target(TargetFilter(GameObjectFilter.ArtifactOrEnchantment.controlledByTriggeringPlayer()))
        effect = Effects.Destroy(victim)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "86"
        artist = "Games Workshop"
    }
}

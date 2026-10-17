package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

/**
 * Jaws, Relentless Predator. "Whenever a noncreature artifact is sacrificed or destroyed" is
 * modelled as "put into a graveyard from the battlefield", which is how those two happen for a
 * noncreature artifact in practice (Treasure, Food, Blood and Clue tokens all qualify).
 */
val JawsRelentlessPredator = card("Jaws, Relentless Predator") {
    manaCost = "{3}{R}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Shark"
    power = 5
    toughness = 5
    oracleText = "Trample, haste\nWhenever Jaws deals combat damage to a player, create that many Blood " +
        "tokens. (They're artifacts with \"{1}, {T}, Discard a card, Sacrifice this token: Draw a card.\")\n" +
        "Whenever a noncreature artifact is sacrificed or destroyed, Jaws deals 1 damage to each opponent."

    keywords(Keyword.TRAMPLE, Keyword.HASTE)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.CreateBlood(DynamicAmounts.triggerDamageAmount())
        description = "Whenever Jaws deals combat damage to a player, create that many Blood tokens."
    }

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Artifact.notCreature()).dies()
        effect = Effects.DealDamage(1, EffectTarget.PlayerRef(Player.EachOpponent))
        description = "Whenever a noncreature artifact is sacrificed or destroyed, Jaws deals 1 damage to each opponent."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2175"
        artist = "Stephen Andrade"
    }
}

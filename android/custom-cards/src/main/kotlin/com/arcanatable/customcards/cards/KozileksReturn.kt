package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private fun damageEachCreature(amount: Int) = Effects.ForEachInGroup(
    GroupFilter(GameObjectFilter.Creature),
    Effects.DealDamage(amount, EffectTarget.IterationEntity),
)

/**
 * Kozilek's Return — OGW #98.
 * The graveyard trigger follows Ugin's Binding: it only exists in the graveyard, re-checks that the
 * card is still there on resolution, and the 5 damage happens only if the exile actually happened.
 */
// CARDKIT-SCAFFOLD — unregistered: implemented by Claude, awaiting CI scenario evidence before registration.
val KozileksReturn = card("Kozilek's Return") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Devoid (This card has no color.)\n" +
        "Kozilek's Return deals 2 damage to each creature.\n" +
        "Whenever you cast an Eldrazi creature spell with mana value 7 or greater, you may exile this card from your graveyard. If you do, this card deals 5 damage to each creature."

    keywords(Keyword.DEVOID)

    spell {
        effect = damageEachCreature(2)
    }

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Creature.withSubtype("Eldrazi").manaValueAtLeast(7))
        triggerZone = Zone.GRAVEYARD
        effect = Effects.If(
            Conditions.SourceInZone(Zone.GRAVEYARD),
            Effects.May(
                Effects.IfYouDo(
                    action = Effects.Exile(EffectTarget.Self, fromZone = Zone.GRAVEYARD),
                    then = damageEachCreature(5),
                ),
                descriptionOverride = "You may exile Kozilek's Return from your graveyard. If you do, it deals 5 damage to each creature.",
            ),
        )
        description = "Whenever you cast an Eldrazi creature spell with mana value 7 or greater, you may exile this card from your graveyard. If you do, this card deals 5 damage to each creature."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "98"
        artist = "Lius Lasahido"
        imageUri = "https://cards.scryfall.io/normal/front/7/2/72765559-0a78-4aa3-827e-cb4612720991.jpg?1783937909"
    }
}

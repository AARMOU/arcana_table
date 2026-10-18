package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

val SwordOfLightAndShadow = card("Sword of Light and Shadow") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +2/+2 and has protection from white and from black.\n" +
        "Whenever equipped creature deals combat damage to a player, you gain 3 life and you may " +
        "return up to one target creature card from your graveyard to your hand.\nEquip {2}"

    staticAbility { ability = ModifyStats(+2, +2, Filters.EquippedCreature) }
    staticAbility { ability = GrantProtection(Color.WHITE) }
    staticAbility { ability = GrantProtection(Color.BLACK) }
    triggeredAbility {
        trigger = Triggers.attached.dealsCombatDamage(Recipient.Player(Player.Any))
        val creatureCard = target(TargetFilter.CreatureInYourGraveyard, optional = true)
        effect = Effects.GainLife(3) then Effects.ReturnToHandFromGraveyard(creatureCard)
        description = "Whenever equipped creature deals combat damage to a player, you gain 3 life " +
            "and you may return up to one target creature card from your graveyard to your hand."
    }
    equipAbility("{2}")

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "298"
        imageUri = "https://cards.scryfall.io/normal/front/b/3/b378c8bf-85ed-4d28-aec9-4925d12bca4a.jpg?1783930094"
    }
}

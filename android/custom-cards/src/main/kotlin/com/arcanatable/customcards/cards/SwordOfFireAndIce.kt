package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.dsl.Targets

val SwordOfFireAndIce = card("Sword of Fire and Ice") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +2/+2 and has protection from red and from blue.\n" +
        "Whenever equipped creature deals combat damage to a player, this Equipment deals 2 damage " +
        "to any target and you draw a card.\nEquip {2}"

    staticAbility { ability = ModifyStats(+2, +2, Filters.EquippedCreature) }
    staticAbility { ability = GrantProtection(Color.RED) }
    staticAbility { ability = GrantProtection(Color.BLUE) }
    triggeredAbility {
        trigger = Triggers.attached.dealsCombatDamage(Recipient.Player(Player.Any))
        val target = target(Targets.Any)
        effect = Effects.DealDamage(2, target) then Effects.DrawCards(1)
        description = "Whenever equipped creature deals combat damage to a player, this Equipment " +
            "deals 2 damage to any target and you draw a card."
    }
    equipAbility("{2}")

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "297"
        imageUri = "https://cards.scryfall.io/normal/front/2/6/2626d4c9-fcef-4057-a154-7d987a4a4b84.jpg?1783930092"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val SwordOfWarAndPeace = card("Sword of War and Peace") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +2/+2 and has protection from red and from white.\n" +
        "Whenever equipped creature deals combat damage to a player, this Equipment deals damage to " +
        "that player equal to the number of cards in their hand and you gain 1 life for each card in " +
        "your hand.\nEquip {2}"

    staticAbility { ability = ModifyStats(+2, +2, Filters.EquippedCreature) }
    staticAbility { ability = GrantProtection(Color.RED) }
    staticAbility { ability = GrantProtection(Color.WHITE) }
    triggeredAbility {
        trigger = Triggers.attached.dealsCombatDamage(Recipient.Player(Player.Any))
        effect = Effects.DealDamage(
            DynamicAmount.Count(Player.TriggeringPlayer, Zone.HAND),
            EffectTarget.PlayerRef(Player.TriggeringPlayer),
        ) then Effects.GainLife(DynamicAmount.Count(Player.You, Zone.HAND))
        description = "Whenever equipped creature deals combat damage to a player, this Equipment " +
            "deals damage to that player equal to the number of cards in their hand and you gain 1 life " +
            "for each card in your hand."
    }
    equipAbility("{2}")

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "300"
        imageUri = "https://cards.scryfall.io/normal/front/4/6/46e6f50b-4b18-4aab-a7b0-869f25bc4c40.jpg?1783930091"
    }
}

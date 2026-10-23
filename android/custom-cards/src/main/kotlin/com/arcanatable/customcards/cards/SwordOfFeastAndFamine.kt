package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val SwordOfFeastAndFamine = card("Sword of Feast and Famine") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +2/+2 and has protection from black and from green.\n" +
        "Whenever equipped creature deals combat damage to a player, that player discards a card and " +
        "you untap all lands you control.\nEquip {2}"

    staticAbility { ability = ModifyStats(+2, +2, Filters.EquippedCreature) }
    staticAbility { ability = GrantProtection(Color.BLACK) }
    staticAbility { ability = GrantProtection(Color.GREEN) }
    triggeredAbility {
        trigger = Triggers.attached.dealsCombatDamage(Recipient.Player(Player.Any))
        effect = Effects.Discard(1, EffectTarget.PlayerRef(Player.TriggeringPlayer)) then
            Effects.ForEachInGroup(
                GroupFilter(GameObjectFilter.Land.youControl()),
                Effects.Untap(EffectTarget.IterationEntity),
            )
        description = "Whenever equipped creature deals combat damage to a player, that player " +
            "discards a card and you untap all lands you control."
    }
    equipAbility("{2}")

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "296"
        imageUri = "https://cards.scryfall.io/normal/front/c/7/c7710eb5-c56a-437b-8847-2a829c404d47.jpg?1783930094"
    }
}

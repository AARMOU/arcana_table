package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val SwordOfBodyAndMind = card("Sword of Body and Mind") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +2/+2 and has protection from green and from blue.\n" +
        "Whenever equipped creature deals combat damage to a player, you create a 2/2 green Wolf " +
        "creature token and that player mills ten cards.\nEquip {2}"

    staticAbility {
        ability = ModifyStats(+2, +2, Filters.EquippedCreature)
    }
    staticAbility {
        ability = GrantProtection(Color.GREEN)
    }
    staticAbility {
        ability = GrantProtection(Color.BLUE)
    }
    triggeredAbility {
        trigger = Triggers.attached.dealsCombatDamage(Recipient.Player(Player.Any))
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Wolf"),
            name = "Wolf",
        ) then Patterns.Library.mill(
            10,
            EffectTarget.PlayerRef(Player.TriggeringPlayer),
        )
        description = "Whenever equipped creature deals combat damage to a player, you create a 2/2 " +
            "green Wolf creature token and that player mills ten cards."
    }
    equipAbility("{2}")

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "295"
        imageUri = "https://cards.scryfall.io/normal/front/1/f/1ffe5f80-a2ac-41b8-8fce-fd3485643557.jpg?1783930094"
    }
}

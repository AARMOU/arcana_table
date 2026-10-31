package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val TangleweaveArmor = card("Tangleweave Armor") {
    manaCost = "{2}{G}{G}"
    colorIdentity = "G"
    typeLine = "Artifact — Equipment"
    oracleText = "Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)\nEquipped creature gets +X/+X, where X is the greatest mana value among your commanders.\nEquip {4}"

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            power = 0,
            toughness = 0,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Phyrexian", "Germ"),
        ) then Effects.AttachEquipment(EffectTarget.PipelineTarget(CREATED_TOKENS, 0))
        description = "Living weapon (When this Equipment enters, create a 0/0 black Phyrexian " +
            "Germ creature token, then attach this to it.)"
    }

    val commandersYouOwn = GameObjectFilter.Any.commander().ownedByYou()
    val commanderManaValues = listOf(
        DynamicAmounts.battlefield(Player.Each, commandersYouOwn).maxManaValue(),
        DynamicAmounts.zone(Player.You, Zone.COMMAND, commandersYouOwn).maxManaValue(),
        DynamicAmounts.zone(Player.You, Zone.GRAVEYARD, commandersYouOwn).maxManaValue(),
        DynamicAmounts.zone(Player.You, Zone.HAND, commandersYouOwn).maxManaValue(),
        DynamicAmounts.zone(Player.You, Zone.LIBRARY, commandersYouOwn).maxManaValue(),
        DynamicAmounts.zone(Player.You, Zone.EXILE, commandersYouOwn).maxManaValue(),
        DynamicAmounts.zone(Player.You, Zone.STACK, commandersYouOwn).maxManaValue(),
    )
    val greatestCommanderManaValue = commanderManaValues.reduce(DynamicAmounts::max)

    staticAbility {
        ability = GrantDynamicStats(
            filter = Filters.EquippedCreature,
            powerBonus = greatestCommanderManaValue,
            toughnessBonus = greatestCommanderManaValue,
        )
    }

    equipAbility("{4}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "234"
        artist = "Johann Bodin"
    }
}

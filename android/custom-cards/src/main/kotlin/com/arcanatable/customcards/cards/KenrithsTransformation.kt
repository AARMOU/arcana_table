package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.LoseAllAbilities
import com.wingedsheep.sdk.scripting.SetBasePowerToughnessStatic
import com.wingedsheep.sdk.scripting.TransformPermanent
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Kenrith's Transformation — AFC #162.
 *
 * The enchanted-creature statics follow Trickster's Elk: Layer 4/5 replace every card type with
 * Creature, every subtype with Elk and the color with green; Layer 6 removes all abilities; Layer 7b
 * sets base power and toughness to 3/3.
 */
val KenrithsTransformation = card("Kenrith's Transformation") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nWhen this Aura enters, draw a card.\nEnchanted creature loses all abilities and is a green Elk creature with base power and toughness 3/3. (It loses all other card types and creature types.)"

    auraTarget = TargetObject(filter = TargetFilter.Creature)

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.DrawCards(1)
    }

    staticAbility {
        ability = TransformPermanent(
            setCardTypes = setOf("CREATURE"),
            setSubtypes = setOf(Subtype.ELK.value),
            setColors = setOf(Color.GREEN),
        )
    }
    staticAbility { ability = LoseAllAbilities() }
    staticAbility { ability = SetBasePowerToughnessStatic(3, 3) }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "162"
        artist = "Kimonas Theodossiou"
    }
}

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

val FoodChain = card("Food Chain") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "Exile a creature you control: Add X mana of any one color, where X is 1 plus the exiled " +
        "creature's mana value. Spend this mana only to cast creature spells."

    activatedAbility {
        cost = Costs.ExilePermanentsFixed(1, GameObjectFilter.Creature.youControl())
        effect = Effects.AddAnyColorMana(
            DynamicAmount.Fixed(1) + DynamicAmount.EntityProperty(EffectTarget.ExiledAsCost(0), EntityNumericProperty.ManaValue),
            ManaRestriction.CreatureSpellsOnly,
        )
        // Not a mana ability here: Arcana's pinned engine only hands an ability the card it exiled
        // as a cost when the ability uses the stack. Activate it, then cast the creature with the
        // floating mana.
        description = "Exile a creature you control: Add X mana of any one color, where X is 1 plus the " +
            "exiled creature's mana value. Spend this mana only to cast creature spells."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "103"
        imageUri = "https://cards.scryfall.io/normal/front/1/8/18a1bb9e-006c-495e-8f99-d451183d2669.jpg?1783945925"
        artist = "Viktor Titov"
    }
}
